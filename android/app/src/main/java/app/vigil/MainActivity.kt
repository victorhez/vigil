package app.vigil

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import app.vigil.ui.VigilApp
import app.vigil.ui.VigilViewModel
import app.vigil.ui.theme.VigilTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : FragmentActivity() {
    private val viewModel: VigilViewModel by viewModels()

    // Must be created before the activity is STARTED: it registers an activity-result launcher.
    private lateinit var sender: ActivityResultSender

    private val checkInRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sender = ActivityResultSender(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)

        setContent {
            VigilTheme {
                VigilApp(viewModel, this, sender, checkInRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_CHECK_IN) checkInRequests.intValue++
    }

    companion object {
        const val ACTION_CHECK_IN = "app.vigil.action.CHECK_IN"
    }
}
