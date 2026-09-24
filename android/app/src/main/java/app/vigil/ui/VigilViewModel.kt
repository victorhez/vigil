package app.vigil.ui

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vigil.BuildConfig
import app.vigil.VigilApplication
import app.vigil.data.Asset
import app.vigil.data.LAMPORTS_PER_SOL
import app.vigil.data.VaultSnapshot
import app.vigil.security.Presence
import app.vigil.security.PresenceResult
import app.vigil.solana.Heir
import app.vigil.solana.Instruction
import app.vigil.solana.PublicKey
import app.vigil.solana.RpcException
import app.vigil.solana.SignatureInfo
import app.vigil.solana.SystemProgram
import app.vigil.solana.TokenProgram
import app.vigil.solana.VaultAccount
import app.vigil.solana.VaultPhase
import app.vigil.solana.VigilProgram
import app.vigil.system.Haptics
import app.vigil.system.ReminderScheduler
import app.vigil.wallet.ConnectedWallet
import app.vigil.wallet.WalletException
import app.vigil.widget.VigilWidget
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Busy { Connecting, Lighting, CheckingIn, Depositing, Withdrawing, Saving, Rotating, Refueling, Airdropping, Releasing, Closing }

data class HeirDraft(val address: String = "", val label: String = "", val shareBps: Int = 0) {
    val publicKey: PublicKey? get() = PublicKey.parseOrNull(address)
}

data class VigilPlan(
    val interval: Long,
    val grace: Long,
    val heirs: List<HeirDraft>,
    val solLamports: Long,
    val tokens: List<Pair<Asset, Long>>,
)

data class CheckInResult(val streak: Int, val signature: String, val at: Long)

data class UiMessage(val text: String, val isError: Boolean = false, val signature: String? = null)

data class VigilState(
    val onboarded: Boolean = false,
    val wallet: ConnectedWallet? = null,
    val vault: VaultAccount? = null,
    val vaultLoaded: Boolean = false,
    val vaultAssets: List<Asset> = emptyList(),
    val walletAssets: List<Asset> = emptyList(),
    val activity: List<SignatureInfo> = emptyList(),
    val legacies: List<VaultAccount> = emptyList(),
    val legacyAssets: Map<PublicKey, List<Asset>> = emptyMap(),
    val pulseKey: PublicKey? = null,
    val pulseKeyLamports: Long = 0,
    val busy: Busy? = null,
    val refreshing: Boolean = false,
    val lastCheckIn: CheckInResult? = null,
    val remindersEnabled: Boolean = true,
) {
    /** True when this phone holds the key the vault expects for check-ins. */
    val deviceLinked: Boolean get() = vault != null && pulseKey != null && vault.pulseKey == pulseKey

    /** How many more check-ins the pulse key can pay for. */
    val pulseFuel: Long get() = pulseKeyLamports / PULSE_FEE_LAMPORTS
}

const val PULSE_FEE_LAMPORTS = 5_000L
const val PULSE_FUEL_LAMPORTS = 10_000_000L

class VigilViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as VigilApplication).container
    private val prefs = container.prefs
    private val repo = container.repository
    private val wallet = container.wallet
    private val keys = container.pulseKeys

    private val _state = MutableStateFlow(
        VigilState(
            onboarded = prefs.onboarded,
            wallet = prefs.wallet?.let { ConnectedWallet(it, prefs.walletLabel) },
            pulseKey = keys.publicKey,
            remindersEnabled = prefs.remindersEnabled,
        ),
    )
    val state: StateFlow<VigilState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun heirLabel(wallet: PublicKey): String? = prefs.heirLabel(wallet)

    fun finishOnboarding() {
        prefs.onboarded = true
        _state.update { it.copy(onboarded = true) }
    }

    fun refresh() {
        val owner = _state.value.wallet?.publicKey ?: run {
            _state.update { it.copy(vaultLoaded = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val vaultJob = async { repo.vaultOf(owner) }
                val walletAssetsJob = async { runCatching { repo.walletAssets(owner) }.getOrNull() }
                val legaciesJob = async { runCatching { repo.legaciesFor(owner) }.getOrNull() }
                val pulseBalanceJob = async { keys.publicKey?.let { runCatching { repo.rpc.getBalance(it) }.getOrNull() } }

                val vault = vaultJob.await()
                val vaultAssets = vault?.let { runCatching { repo.vaultAssets(it) }.getOrNull() } ?: emptyList()
                val activity = vault?.let { repo.activity(it.address) } ?: emptyList()
                val legacies = legaciesJob.await() ?: _state.value.legacies
                val legacyAssets = legacies.associate { v ->
                    v.address to (runCatching { repo.vaultAssets(v) }.getOrNull() ?: emptyList())
                }

                prefs.snapshot = vault?.let(VaultSnapshot::of)
                VigilWidget.refresh(getApplication())
                ReminderScheduler.sync(getApplication())

                _state.update {
                    it.copy(
                        vault = vault,
                        vaultLoaded = true,
                        vaultAssets = vaultAssets,
                        walletAssets = walletAssetsJob.await() ?: it.walletAssets,
                        activity = activity,
                        legacies = legacies,
                        legacyAssets = legacyAssets,
                        pulseKey = keys.publicKey,
                        pulseKeyLamports = pulseBalanceJob.await() ?: it.pulseKeyLamports,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(vaultLoaded = true) }
                emitError(e)
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun connect(sender: ActivityResultSender) = run(Busy.Connecting) {
        val connected = wallet.connect(sender)
        prefs.wallet = connected.publicKey
        prefs.walletLabel = connected.label
        _state.update { it.copy(wallet = connected, vaultLoaded = false, vault = null) }
        refresh()
    }

    fun disconnect(sender: ActivityResultSender) = run(null) {
        wallet.disconnect(sender)
        prefs.clearSession()
        VigilWidget.refresh(getApplication())
        _state.update {
            VigilState(onboarded = true, pulseKey = keys.publicKey, remindersEnabled = it.remindersEnabled, vaultLoaded = true)
        }
    }

    /** Creates the vault, funds the device pulse key and makes the first deposits in one wallet approval. */
    fun light(sender: ActivityResultSender, plan: VigilPlan) = run(Busy.Lighting) {
        val owner = requireOwner()
        val pulseKey = keys.publicKey?.takeIf { keys.exists() } ?: keys.create()
        val vault = VigilProgram.vaultAddress(owner)
        val heirs = plan.heirs.map { Heir(it.publicKey!!, it.shareBps) }
        plan.heirs.forEach { prefs.setHeirLabel(it.publicKey!!, it.label) }

        val ixs = buildList {
            add(SystemProgram.transfer(owner, pulseKey, PULSE_FUEL_LAMPORTS))
            add(VigilProgram.createVault(owner, plan.interval, plan.grace, pulseKey, heirs))
            if (plan.solLamports > 0) add(VigilProgram.deposit(owner, vault, plan.solLamports))
            plan.tokens.filter { it.second > 0 }.forEach { (asset, amount) -> addAll(tokenDeposit(owner, vault, asset, amount)) }
        }
        submit(sender, owner, ixs, "Your Vigil is lit.")
        Haptics.heartbeat(getApplication())
    }

    /** One-tap proof of life, signed on-device by the pulse key after a biometric check. */
    fun checkIn(activity: FragmentActivity, sender: ActivityResultSender) = run(Busy.CheckingIn) {
        val current = _state.value
        val vault = current.vault ?: throw IllegalStateException("No vault yet.")
        val now = System.currentTimeMillis() / 1000

        if (!current.deviceLinked || vault.phase(now) == VaultPhase.Expired) {
            // Only the owner wallet may revive an expired vault, and an unlinked phone has no key to sign with.
            submit(sender, vault.owner, listOf(VigilProgram.pulse(vault.owner, vault.address)), null)
            celebrate()
            return@run
        }

        if (keys.requiresUserPresence) {
            when (val result = Presence.confirm(activity, "Clock in", "Confirm it's you to check in")) {
                PresenceResult.Confirmed -> Unit
                PresenceResult.Cancelled -> return@run
                is PresenceResult.Failed -> throw IllegalStateException(result.message)
            }
        }
        if (current.pulseKeyLamports in 1 until PULSE_FEE_LAMPORTS * 2) {
            throw IllegalStateException("This phone's pulse key is out of fuel. Top it up in Settings.")
        }

        val signer = keys.unlock()
        val signature = repo.sendSigned(signer, listOf(VigilProgram.pulse(signer.publicKey, vault.address)))
        celebrate(signature)
    }

    private suspend fun celebrate(signature: String? = null) {
        Haptics.heartbeat(getApplication())
        val owner = requireOwner()
        val vault = repo.vaultOf(owner)
        prefs.snapshot = vault?.let(VaultSnapshot::of)
        VigilWidget.refresh(getApplication())
        _state.update {
            it.copy(
                vault = vault ?: it.vault,
                lastCheckIn = CheckInResult(vault?.streak ?: 1, signature ?: "", System.currentTimeMillis()),
            )
        }
        refresh()
    }

    fun consumeCheckIn() = _state.update { it.copy(lastCheckIn = null) }

    fun deposit(sender: ActivityResultSender, asset: Asset, amount: Long) = run(Busy.Depositing) {
        val owner = requireOwner()
        val vault = VigilProgram.vaultAddress(owner)
        val ixs = if (asset.isSol) listOf(VigilProgram.deposit(owner, vault, amount)) else tokenDeposit(owner, vault, asset, amount)
        submit(sender, owner, ixs, "Deposited ${asset.symbol} into your vault.")
    }

    fun withdraw(sender: ActivityResultSender, asset: Asset, amount: Long) = run(Busy.Withdrawing) {
        val owner = requireOwner()
        val ixs = if (asset.isSol) {
            listOf(VigilProgram.withdraw(owner, amount))
        } else {
            listOf(
                TokenProgram.createAssociatedTokenAccountIdempotent(owner, owner, asset.mint!!, asset.tokenProgram),
                VigilProgram.withdrawToken(owner, asset.mint, asset.tokenProgram, amount),
            )
        }
        submit(sender, owner, ixs, "Withdrew ${asset.symbol} to your wallet.")
    }

    fun saveConfig(sender: ActivityResultSender, interval: Long, grace: Long, heirs: List<HeirDraft>) = run(Busy.Saving) {
        val owner = requireOwner()
        heirs.forEach { prefs.setHeirLabel(it.publicKey!!, it.label) }
        submit(
            sender,
            owner,
            listOf(VigilProgram.configure(owner, interval, grace, heirs.map { Heir(it.publicKey!!, it.shareBps) })),
            "Vault updated. That signature also counted as a check-in.",
        )
    }

    /** Binds check-ins to a fresh key on this phone, e.g. after a reinstall or a new device. */
    fun linkThisPhone(sender: ActivityResultSender) = run(Busy.Rotating) {
        val owner = requireOwner()
        val pulseKey = keys.create()
        _state.update { it.copy(pulseKey = pulseKey, pulseKeyLamports = 0) }
        submit(
            sender,
            owner,
            listOf(
                SystemProgram.transfer(owner, pulseKey, PULSE_FUEL_LAMPORTS),
                VigilProgram.rotatePulseKey(owner, pulseKey),
            ),
            "This phone can now check in for your vault.",
        )
    }

    fun refuel(sender: ActivityResultSender) = run(Busy.Refueling) {
        val owner = requireOwner()
        val pulseKey = keys.publicKey ?: throw IllegalStateException("No pulse key on this phone.")
        submit(sender, owner, listOf(SystemProgram.transfer(owner, pulseKey, PULSE_FUEL_LAMPORTS)), "Pulse key topped up with 0.01 SOL.")
    }

    fun airdrop() = run(Busy.Airdropping) {
        val owner = requireOwner()
        val signature = try {
            repo.rpc.requestAirdrop(owner, LAMPORTS_PER_SOL)
        } catch (e: RpcException) {
            throw IllegalStateException("The devnet faucet is rate-limited right now. Try faucet.solana.com.")
        }
        repo.rpc.confirm(signature)
        _messages.tryEmit(UiMessage("1 devnet SOL added to your wallet.", signature = signature))
        refresh()
    }

    /** Pushes an expired vault's SOL and tokens to its heirs. Anyone can do this; the heirs are fixed on-chain. */
    fun release(sender: ActivityResultSender, vault: VaultAccount) = run(Busy.Releasing) {
        val caller = requireOwner()
        val assets = repo.vaultAssets(vault)
        val batches = buildList {
            if (assets.firstOrNull { it.isSol }?.amount?.let { it > 0 } == true) add(listOf(VigilProgram.releaseSol(caller, vault)))
            assets.filter { !it.isSol && it.amount > 0 }.forEach { asset ->
                add(
                    vault.heirs.map {
                        TokenProgram.createAssociatedTokenAccountIdempotent(caller, it.wallet, asset.mint!!, asset.tokenProgram)
                    } + VigilProgram.releaseToken(caller, vault, asset.mint!!, asset.tokenProgram),
                )
            }
        }
        if (batches.isEmpty()) throw IllegalStateException("This vault has nothing left to release.")
        val txs = batches.map { repo.buildForWallet(caller, it) }
        val signatures = wallet.signAndSend(sender, caller, txs)
        signatures.forEach { repo.rpc.confirm(it) }
        Haptics.confirm(getApplication())
        _messages.tryEmit(UiMessage("Released to ${vault.heirs.size} heir${if (vault.heirs.size == 1) "" else "s"}.", signature = signatures.last()))
        refresh()
    }

    fun closeVault(sender: ActivityResultSender) = run(Busy.Closing) {
        val owner = requireOwner()
        val vault = _state.value.vault ?: return@run
        val tokenExits = repo.vaultAssets(vault).filter { !it.isSol && it.amount > 0 }.flatMap { asset ->
            listOf(
                TokenProgram.createAssociatedTokenAccountIdempotent(owner, owner, asset.mint!!, asset.tokenProgram),
                VigilProgram.withdrawToken(owner, asset.mint, asset.tokenProgram, asset.amount),
            )
        }
        submit(sender, owner, tokenExits + VigilProgram.closeVault(owner), "Vault closed. Everything is back in your wallet.")
        prefs.snapshot = null
        VigilWidget.refresh(getApplication())
    }

    fun setReminders(enabled: Boolean) {
        prefs.remindersEnabled = enabled
        _state.update { it.copy(remindersEnabled = enabled) }
        ReminderScheduler.sync(getApplication())
    }

    private fun tokenDeposit(owner: PublicKey, vault: PublicKey, asset: Asset, amount: Long): List<Instruction> {
        val mint = asset.mint!!
        return listOf(
            TokenProgram.createAssociatedTokenAccountIdempotent(owner, vault, mint, asset.tokenProgram),
            TokenProgram.transferChecked(
                source = TokenProgram.associatedTokenAddress(owner, mint, asset.tokenProgram),
                mint = mint,
                destination = TokenProgram.associatedTokenAddress(vault, mint, asset.tokenProgram),
                authority = owner,
                amount = amount,
                decimals = asset.decimals,
                tokenProgram = asset.tokenProgram,
            ),
        )
    }

    /** Has the owner's wallet sign and submit `ixs`, then waits for confirmation. */
    private suspend fun submit(sender: ActivityResultSender, owner: PublicKey, ixs: List<Instruction>, success: String?) {
        val tx = repo.buildForWallet(owner, ixs)
        val signature = wallet.signAndSend(sender, owner, listOf(tx)).first()
        repo.rpc.confirm(signature)
        if (success != null) _messages.tryEmit(UiMessage(success, signature = signature))
        refresh()
    }

    private fun requireOwner(): PublicKey =
        _state.value.wallet?.publicKey ?: throw IllegalStateException("Connect a wallet first.")

    private fun run(busy: Busy?, block: suspend () -> Unit) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = busy) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    private fun emitError(e: Exception) {
        val text = when (e) {
            is WalletException -> e.message
            is RpcException -> programError(e) ?: "Network error: ${e.message}"
            else -> e.message
        } ?: "Something went wrong."
        _messages.tryEmit(UiMessage(text, isError = true))
    }

    private fun programError(e: RpcException): String? {
        val line = e.logs.firstOrNull { it.contains("Error Message:") } ?: return null
        return line.substringAfter("Error Message:").trim().trimEnd('.') + "."
    }

    companion object {
        val isDevnet: Boolean get() = BuildConfig.CLUSTER != "mainnet-beta"
    }
}
