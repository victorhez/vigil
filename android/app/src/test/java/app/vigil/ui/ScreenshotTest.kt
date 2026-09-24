package app.vigil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import java.util.concurrent.Executors
import app.vigil.VigilApplication
import app.vigil.data.Asset
import app.vigil.data.KnownTokens
import app.vigil.data.LAMPORTS_PER_SOL
import app.vigil.solana.Heir
import app.vigil.solana.PublicKey
import app.vigil.solana.SignatureInfo
import app.vigil.solana.VaultAccount
import app.vigil.solana.VigilProgram
import app.vigil.ui.screens.OnboardingScreen
import app.vigil.ui.screens.SetupScreen
import app.vigil.ui.theme.Vigil
import app.vigil.ui.theme.VigilTheme
import app.vigil.wallet.ConnectedWallet
import com.github.takahirom.roborazzi.captureRoboImage
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** WorkManager is normally initialised by androidx.startup, which Robolectric does not run. */
class ScreenshotApplication : VigilApplication() {
    override fun onCreate() {
        // One WorkManager per JVM; Robolectric creates a fresh Application for every test.
        runCatching { WorkManager.initialize(this, Configuration.Builder().setExecutor(Executors.newSingleThreadExecutor()).build()) }
        super.onCreate()
    }
}

/**
 * Renders the main screens with representative data into docs/screens.
 * Run with `./gradlew recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-night-xxhdpi", application = ScreenshotApplication::class)
class ScreenshotTest {
    private val now = System.currentTimeMillis() / 1000
    private val me = PublicKey.of("GmaDrppBC7P5ARKV8g3djiwP89vz1jLK23V2GBjuAEGB")
    private val maya = PublicKey.of("GT3iuxs6vmnCGtTiK44Af1sRTFrHHE2uSzTGvTGB9mQo")
    private val theo = PublicKey.of("J3ZeG72VA3aNQKLRKMwM22snTCgXKnTE3gLzzAVSpba3")
    private val dad = PublicKey.of("B6n6eUcbQav2hXthhUBxdaf8DzyR29477JFXjCiMWkur")

    private fun vault(owner: PublicKey, lastPulseAgo: Long, heirs: List<Heir>, streak: Int = 42) = VaultAccount(
        address = VigilProgram.vaultAddress(owner),
        owner = owner,
        pulseKey = me,
        interval = 86_400,
        grace = 86_400,
        createdAt = now - 60 * 86_400,
        lastPulse = now - lastPulseAgo,
        streak = streak,
        bestStreak = 57,
        totalPulses = 58,
        releasedAt = 0,
        heirs = heirs,
        bump = 255,
        lamports = 12_500_000_000,
    )

    private val myVault = vault(me, 5 * 3_600 + 17 * 60, listOf(Heir(maya, 6_000), Heir(theo, 4_000)))
    private val dadsVault = vault(dad, 3 * 86_400, listOf(Heir(me, 5_000), Heir(maya, 5_000)), streak = 0)

    private val sol = Asset(null, "SOL", "Solana", 12_491_000_000, 9)
    private val skr = Asset(KnownTokens.SKR, "SKR", "Seeker", 25_000_000_000, 6, featured = true)
    private val usdc = Asset(PublicKey.of("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"), "USDC", "USD Coin", 1_200_000_000, 6)

    private val state = VigilState(
        onboarded = true,
        wallet = ConnectedWallet(me, "Seed Vault"),
        vault = myVault,
        vaultLoaded = true,
        vaultAssets = listOf(sol, skr, usdc),
        walletAssets = listOf(Asset(null, "SOL", "Solana", 3 * LAMPORTS_PER_SOL, 9), skr.copy(amount = 4_200_000_000)),
        activity = List(6) { i ->
            SignatureInfo("5Kq${"x7Rt2vMpLq9WbNc4".repeat(5)}${i}Zp", now - i * 86_400 - 3_600, false)
        },
        legacies = listOf(dadsVault),
        legacyAssets = mapOf(dadsVault.address to listOf(sol.copy(amount = 4 * LAMPORTS_PER_SOL), skr)),
        pulseKey = me,
        pulseKeyLamports = 9_985_000,
    )

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, content: @Composable (VigilViewModel) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<VigilApplication>()
        app.container.prefs.setHeirLabel(maya, "Maya Okafor")
        app.container.prefs.setHeirLabel(theo, "Theo")
        app.container.prefs.setHeirLabel(dad, "Dad")
        val viewModel = VigilViewModel(app)
        // Never started, so it can still register the wallet's activity-result launcher.
        val host = FragmentActivity()
        val sender = ActivityResultSender(host)

        // The pulse dial animates forever; drive the clock by hand instead of waiting for idle.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VigilTheme {
                CompositionLocalProvider(LocalSender provides sender, LocalHostActivity provides host) {
                    Box(Modifier.fillMaxSize().background(Vigil.Ink)) { content(viewModel) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onRoot().captureRoboImage("../../docs/screens/$name.png")
    }

    @Test fun onboarding() = shot("01-onboarding") { OnboardingScreen(state.copy(wallet = null, onboarded = false), it) }

    @Test fun pulse() = shot("02-pulse") { MainShell(state, it, Tab.Pulse, {}, {}, {}, {}, 0) }

    @Test fun vault() = shot("03-vault") { MainShell(state, it, Tab.Vault, {}, {}, {}, {}, 0) }

    @Test fun heirs() = shot("04-heirs") { MainShell(state, it, Tab.Heirs, {}, {}, {}, {}, 0) }

    @Test fun legacies() = shot("05-legacies") { MainShell(state, it, Tab.Legacies, {}, {}, {}, {}, 0) }

    @Test fun setup() = shot("06-setup") { SetupScreen(state.copy(vault = null), it, editing = false, onClose = {}) }

    @Test fun empty() = shot("07-unlit") { MainShell(state.copy(vault = null, activity = emptyList()), it, Tab.Pulse, {}, {}, {}, {}, 0) }
}
