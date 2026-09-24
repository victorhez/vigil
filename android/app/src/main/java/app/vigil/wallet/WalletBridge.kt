package app.vigil.wallet

import android.net.Uri
import app.vigil.BuildConfig
import app.vigil.data.Prefs
import app.vigil.solana.Base58
import app.vigil.solana.PublicKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult

class WalletException(message: String, val noWallet: Boolean = false) : Exception(message)

data class ConnectedWallet(val publicKey: PublicKey, val label: String?)

/**
 * Mobile Wallet Adapter session. The owner's wallet (Seed Vault on Seeker, or any MWA wallet)
 * signs everything that touches funds or configuration.
 */
class WalletBridge(private val prefs: Prefs) {
    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse(IDENTITY_URI),
            iconUri = Uri.parse("favicon.png"),
            identityName = "Vigil",
        ),
    ).apply {
        blockchain = if (BuildConfig.CLUSTER == "mainnet-beta") Solana.Mainnet else Solana.Devnet
        authToken = prefs.authToken
    }

    suspend fun connect(sender: ActivityResultSender): ConnectedWallet {
        val result = adapter.connect(sender)
        val success = result.unwrap()
        val account = success.authResult.accounts.firstOrNull() ?: throw WalletException("Wallet returned no account")
        prefs.authToken = success.authResult.authToken
        return ConnectedWallet(PublicKey(account.publicKey), account.accountLabel)
    }

    /**
     * Has the wallet sign and submit `transactions`. Returns base58 signatures.
     * `expected` guards against the wallet switching accounts mid-session.
     */
    suspend fun signAndSend(
        sender: ActivityResultSender,
        expected: PublicKey,
        transactions: List<ByteArray>,
    ): List<String> {
        val result = adapter.transact(sender) { auth ->
            val account = auth.accounts.firstOrNull()?.publicKey
            if (account == null || !PublicKey(account).equals(expected)) {
                throw WalletException("Your wallet is on a different account. Switch back to ${expected.short()}.")
            }
            signAndSendTransactions(transactions.toTypedArray())
        }
        val success = result.unwrap()
        prefs.authToken = success.authResult.authToken
        return success.payload.signatures.map(Base58::encode)
    }

    suspend fun disconnect(sender: ActivityResultSender) {
        runCatching { adapter.disconnect(sender) }
        adapter.authToken = null
        prefs.authToken = null
    }

    private fun <T> TransactionResult<T>.unwrap(): TransactionResult.Success<T> = when (this) {
        is TransactionResult.Success -> this
        is TransactionResult.NoWalletFound -> throw WalletException(
            "No Solana wallet found. Install a Mobile Wallet Adapter wallet to continue.",
            noWallet = true,
        )
        is TransactionResult.Failure -> {
            val cause = e.cause ?: e
            if (cause is WalletException) throw cause
            throw WalletException(friendly(message, cause))
        }
    }

    private fun friendly(message: String, cause: Throwable): String = when {
        message.contains("not authorize", true) || message.contains("declined", true) -> "Request declined in wallet."
        message.contains("cancel", true) || message.contains("interrupt", true) -> "Wallet request cancelled."
        message.contains("Auth token", true) -> "Wallet session expired. Connect again."
        else -> cause.message?.takeIf { it.isNotBlank() && it.length < 140 } ?: message
    }

    companion object {
        const val IDENTITY_URI = "https://vigil-seeker.vercel.app"
    }
}
