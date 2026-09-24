package app.vigil.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vigil.BuildConfig
import app.vigil.ui.components.Icons
import app.vigil.ui.components.PulseDial
import app.vigil.ui.screens.HeirsScreen
import app.vigil.ui.screens.LegaciesScreen
import app.vigil.ui.screens.OnboardingScreen
import app.vigil.ui.screens.PulseScreen
import app.vigil.ui.screens.SettingsScreen
import app.vigil.ui.screens.SetupScreen
import app.vigil.ui.screens.VaultScreen
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import app.vigil.solana.VaultPhase
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.delay

val LocalSender = staticCompositionLocalOf<ActivityResultSender> { error("No ActivityResultSender") }
val LocalHostActivity = staticCompositionLocalOf<FragmentActivity> { error("No activity") }

enum class Tab(val label: String, val icon: ImageVector) {
    Pulse("Pulse", Icons.Pulse),
    Vault("Vault", Icons.Vault),
    Heirs("Heirs", Icons.Heirs),
    Legacies("Legacies", Icons.Legacy),
}

private enum class Route { Main, Setup, Edit, Settings }

fun explorerUrl(signatureOrAddress: String, isTx: Boolean = true): String {
    val cluster = if (BuildConfig.CLUSTER == "mainnet-beta") "" else "?cluster=${BuildConfig.CLUSTER}"
    return "https://explorer.solana.com/${if (isTx) "tx" else "address"}/$signatureOrAddress$cluster"
}

@Composable
fun VigilApp(
    viewModel: VigilViewModel,
    activity: FragmentActivity,
    sender: ActivityResultSender,
    checkInRequests: Int,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(Route.Main) }
    var tab by rememberSaveable { mutableStateOf(Tab.Pulse) }
    var message by remember { mutableStateOf<UiMessage?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message = it }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(if (message!!.isError) 5200 else 3800)
            message = null
        }
    }
    // Widget, tile and shortcut launches land here: go straight to the check-in ritual.
    LaunchedEffect(checkInRequests) {
        if (checkInRequests > 0) {
            route = Route.Main
            tab = Tab.Pulse
        }
    }

    CompositionLocalProvider(LocalSender provides sender, LocalHostActivity provides activity) {
        Box(Modifier.fillMaxSize().background(Vigil.Ink)) {
            when {
                !state.onboarded || state.wallet == null -> OnboardingScreen(state, viewModel)
                !state.vaultLoaded -> Booting()
                else -> {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                        label = "route",
                    ) { current ->
                        when (current) {
                            Route.Main -> MainShell(
                                state = state,
                                viewModel = viewModel,
                                tab = tab,
                                onTab = { tab = it },
                                onLight = { route = Route.Setup },
                                onEdit = { route = Route.Edit },
                                onSettings = { route = Route.Settings },
                                autoCheckIn = checkInRequests,
                            )
                            Route.Setup -> SetupScreen(state, viewModel, editing = false, onClose = { route = Route.Main })
                            Route.Edit -> SetupScreen(state, viewModel, editing = true, onClose = { route = Route.Main })
                            Route.Settings -> SettingsScreen(state, viewModel, onClose = { route = Route.Main })
                        }
                    }
                    BackHandler(enabled = route != Route.Main) { route = Route.Main }
                }
            }

            MessageBanner(message, onDismiss = { message = null }, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun Booting() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PulseDial(remaining = 1f, phase = VaultPhase.Alive, hold = 0f, burstKey = null, modifier = Modifier.width(180.dp))
    }
}

@Composable
private fun MainShell(
    state: VigilState,
    viewModel: VigilViewModel,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onLight: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    autoCheckIn: Int,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                Tab.Pulse -> PulseScreen(state, viewModel, onLight, onSettings, autoCheckIn)
                Tab.Vault -> VaultScreen(state, viewModel, onLight, onSettings)
                Tab.Heirs -> HeirsScreen(state, viewModel, onLight, onEdit, onSettings)
                Tab.Legacies -> LegaciesScreen(state, viewModel, onSettings)
            }
        }
        TabBar(
            selected = tab,
            onSelect = onTab,
            badge = state.legacies.any { it.phase(System.currentTimeMillis() / 1000) == VaultPhase.Expired },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, badge: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Vigil.Ink.copy(alpha = 0.92f), Vigil.Ink)))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(Vigil.Surface)
                .border(1.dp, Vigil.Line, shape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { t ->
                val active = t == selected
                Row(
                    Modifier
                        .clip(shape)
                        .background(if (active) Vigil.Raised else Color.Transparent)
                        .clickable { onSelect(t) }
                        .padding(horizontal = if (active) 16.dp else 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        androidx.compose.material3.Icon(t.icon, contentDescription = t.label, tint = if (active) Vigil.Flame else Vigil.Muted, modifier = Modifier.size(20.dp))
                        if (t == Tab.Legacies && badge) {
                            Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(RoundedCornerShape(50)).background(Vigil.Blood))
                        }
                    }
                    AnimatedVisibility(active) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, style = Type.Label, color = Vigil.Bone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: UiMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val m = last ?: return@AnimatedVisibility
        val accent = if (m.isError) Vigil.Blood else Vigil.Flame
        val shape = RoundedCornerShape(20.dp)
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Vigil.Raised)
                .border(1.dp, accent.copy(alpha = 0.4f), shape)
                .clickable {
                    val sig = m.signature
                    if (sig != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl(sig))))
                    onDismiss()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.text, style = Type.Body)
                if (m.signature != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("View on Solana Explorer", style = Type.Label, color = accent)
                }
            }
        }
    }
}
