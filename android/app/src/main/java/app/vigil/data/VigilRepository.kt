package app.vigil.data

import app.vigil.solana.Instruction
import app.vigil.solana.Keypair
import app.vigil.solana.Programs
import app.vigil.solana.PublicKey
import app.vigil.solana.SignatureInfo
import app.vigil.solana.SolanaRpc
import app.vigil.solana.Transaction
import app.vigil.solana.VaultAccount
import app.vigil.solana.VigilProgram
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Reads Vigil state from the chain and prepares transactions. Stateless apart from the RPC client. */
class VigilRepository(val rpc: SolanaRpc) {

    suspend fun vaultOf(owner: PublicKey): VaultAccount? = vaultAt(VigilProgram.vaultAddress(owner))

    suspend fun vaultAt(address: PublicKey): VaultAccount? =
        rpc.getAccountInfo(address)?.takeIf { it.owner == VigilProgram.ID }?.let { VigilProgram.decode(address, it) }

    /** Everything the vault holds. SOL excludes the rent-exempt reserve, which can never be withdrawn. */
    suspend fun vaultAssets(vault: VaultAccount): List<Asset> = coroutineScope {
        val rent = async { rpc.getMinimumBalanceForRentExemption(VigilProgram.ACCOUNT_SIZE) }
        val tokens = tokenAssets(vault.address)
        val sol = Asset(null, "SOL", "Solana", (vault.lamports - rent.await()).coerceAtLeast(0), 9)
        listOf(sol) + tokens
    }

    suspend fun walletAssets(owner: PublicKey): List<Asset> = coroutineScope {
        val balance = async { rpc.getBalance(owner) }
        val tokens = tokenAssets(owner)
        listOf(Asset(null, "SOL", "Solana", balance.await(), 9)) + tokens
    }

    private suspend fun tokenAssets(owner: PublicKey): List<Asset> = coroutineScope {
        listOf(Programs.TOKEN, Programs.TOKEN_2022)
            .map { program -> async { runCatching { rpc.getTokenAccountsByOwner(owner, program) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
            .filter { it.amount > 0 }
            .groupBy { it.mint }
            .map { (mint, accounts) ->
                val info = KnownTokens.info(mint)
                Asset(
                    mint = mint,
                    symbol = info.symbol,
                    name = info.name,
                    amount = accounts.sumOf { it.amount },
                    decimals = accounts.first().decimals,
                    tokenProgram = accounts.first().tokenProgram,
                    featured = info.featured,
                )
            }
            .sortedWith(compareByDescending<Asset> { it.featured }.thenBy { it.symbol })
    }

    suspend fun activity(vault: PublicKey, limit: Int = 30): List<SignatureInfo> =
        runCatching { rpc.getSignaturesForAddress(vault, limit) }.getOrDefault(emptyList())

    /** Vaults that name `heir` in any of their five heir slots. */
    suspend fun legaciesFor(heir: PublicKey): List<VaultAccount> = coroutineScope {
        (0 until VigilProgram.MAX_HEIRS).map { slot ->
            async {
                runCatching {
                    rpc.getProgramAccounts(
                        VigilProgram.ID,
                        VigilProgram.ACCOUNT_SIZE,
                        listOf(VigilProgram.heirOffset(slot) to heir.bytes),
                    )
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
            .flatten()
            .distinctBy { it.first }
            .mapNotNull { (address, account) -> VigilProgram.decode(address, account) }
            .filter { vault -> vault.heirs.any { it.wallet == heir } }
    }

    /** Unsigned transaction bytes for the wallet to sign and submit. */
    suspend fun buildForWallet(feePayer: PublicKey, instructions: List<Instruction>): ByteArray =
        Transaction(feePayer, rpc.getLatestBlockhash(), instructions).serialize()

    /** Signs locally with `signer` as fee payer, submits, and waits for confirmation. */
    suspend fun sendSigned(signer: Keypair, instructions: List<Instruction>): String {
        val tx = Transaction(signer.publicKey, rpc.getLatestBlockhash(), instructions).sign(signer)
        val signature = rpc.sendTransaction(tx.serialize())
        rpc.confirm(signature)
        return signature
    }
}
