package app.vigil

import android.app.Application
import android.content.Context
import app.vigil.data.Prefs
import app.vigil.data.VigilRepository
import app.vigil.security.PulseKeyStore
import app.vigil.solana.SolanaRpc
import app.vigil.system.ReminderScheduler
import app.vigil.wallet.WalletBridge

class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val repository = VigilRepository(SolanaRpc(BuildConfig.RPC_URL))
    val wallet = WalletBridge(prefs)
    val pulseKeys = PulseKeyStore(context)
}

open class VigilApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        ReminderScheduler.sync(this)
    }
}
