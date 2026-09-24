package app.vigil.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vigil.solana.VaultAccount
import app.vigil.solana.VaultPhase
import app.vigil.ui.Busy
import app.vigil.ui.LocalHostActivity
import app.vigil.ui.LocalSender
import app.vigil.ui.VigilState
import app.vigil.ui.VigilViewModel
import app.vigil.ui.components.ButtonKind
import app.vigil.ui.components.Divider
import app.vigil.ui.components.HeirAvatar
import app.vigil.ui.components.HoldToConfirm
import app.vigil.ui.components.Icons
import app.vigil.ui.components.Overline
import app.vigil.ui.components.Panel
import app.vigil.ui.components.Pill
import app.vigil.ui.components.PulseDial
import app.vigil.ui.components.Stat
import app.vigil.ui.components.TimeText
import app.vigil.ui.components.VigilButton
import app.vigil.ui.components.rememberNow
import app.vigil.ui.explorerUrl
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun PulseScreen(
    state: VigilState,
    viewModel: VigilViewModel,
    onLight: () -> Unit,
    onSettings: () -> Unit,
    autoCheckIn: Int,
) {
    val vault = state.vault
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        else -> "Good evening."
    }
    TabPage(title = if (vault == null) "Light a vigil." else greeting, kicker = if (vault == null) "Welcome" else "Still here?", onSettings = onSettings) {
        if (vault == null) EmptyPulse(onLight) else LivePulse(state, vault, viewModel, autoCheckIn)
    }
}

@Composable
private fun EmptyPulse(onLight: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PulseDial(remaining = 0f, phase = VaultPhase.Alive, hold = 0f, burstKey = null, modifier = Modifier.fillMaxWidth(0.82f)) {
            Text("unlit", style = Type.Title.copy(fontStyle = FontStyle.Italic), color = Vigil.Muted)
        }
    }
    Spacer(Modifier.height(28.dp))
    Text(
        "Choose who inherits your SOL, SKR and tokens, and how often you'll check in. Everything else is automatic.",
        style = Type.BodyMuted,
    )
    Spacer(Modifier.height(24.dp))
    VigilButton("Light your Vigil", onClick = onLight, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    Panel {
        Overline("How it works")
        Spacer(Modifier.height(14.dp))
        Step("1", "Pick heirs and a check-in rhythm. Daily, weekly, monthly.")
        Step("2", "Deposit what you want protected. Withdraw any time.")
        Step("3", "Clock in with a hold and a fingerprint. Miss the window and grace period, and it goes to your heirs.")
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Text(n, style = Type.Numeric, color = Vigil.Flame, modifier = Modifier.width(26.dp))
        Text(text, style = Type.Body)
    }
}

@Composable
private fun LivePulse(state: VigilState, vault: VaultAccount, viewModel: VigilViewModel, autoCheckIn: Int) {
    val now = rememberNow()
    val activity = LocalHostActivity.current
    val sender = LocalSender.current
    val context = LocalContext.current
    val phase = vault.phase(now)
    var hold by remember { mutableFloatStateOf(0f) }
    var celebration by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.lastCheckIn) {
        val result = state.lastCheckIn ?: return@LaunchedEffect
        celebration = result.streak
        delay(2600)
        celebration = null
        viewModel.consumeCheckIn()
    }
    LaunchedEffect(autoCheckIn) {
        if (autoCheckIn > 0 && phase != VaultPhase.Released && state.busy == null) viewModel.checkIn(activity, sender)
    }

    val (remaining, clock, caption) = when (phase) {
        VaultPhase.Alive -> Triple(
            (vault.dueAt - now) / vault.interval.toFloat(),
            TimeText.clock(vault.dueAt - now),
            "until your next check-in",
        )
        VaultPhase.Overdue -> Triple(
            if (vault.grace > 0) (vault.deadline - now) / vault.grace.toFloat() else 0f,
            TimeText.clock(vault.deadline - now),
            "of grace left before release",
        )
        VaultPhase.Expired -> Triple(0f, "00:00:00", "window closed · heirs can claim")
        VaultPhase.Released -> Triple(0f, "—", "released to your heirs")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        val (label, color) = when (phase) {
            VaultPhase.Alive -> (if (remaining < 0.2f) "Due soon" else "Alive") to (if (remaining < 0.2f) Vigil.Ember else Vigil.Moss)
            VaultPhase.Overdue -> "Overdue" to Vigil.Ember
            VaultPhase.Expired -> "Expired" to Vigil.Blood
            VaultPhase.Released -> "Released" to Vigil.Ash
        }
        Pill(label, color, dot = true)
        Spacer(Modifier.weight(1f))
        Text(TimeText.cadence(vault.interval) + if (vault.grace > 0) " · +${TimeText.span(vault.grace)} grace" else " · no grace", style = Type.Label)
    }

    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PulseDial(
            remaining = remaining,
            phase = phase,
            hold = hold,
            burstKey = state.lastCheckIn?.at,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            AnimatedVisibility(celebration == null, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clock, style = Type.Clock)
                    Spacer(Modifier.height(4.dp))
                    Text(caption, style = Type.Label, color = Vigil.Bone.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                }
            }
            AnimatedVisibility(celebration != null, enter = scaleIn(initialScale = 0.8f) + fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Clocked in.", style = Type.Display.copy(fontStyle = FontStyle.Italic), color = Vigil.Ink)
                    Text("Day ${celebration ?: 0}", style = Type.Numeric, color = Vigil.Ink)
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    when (phase) {
        VaultPhase.Released -> Text(
            "This vault was released on ${TimeText.date(vault.releasedAt)}. Your heirs received what it held.",
            style = Type.BodyMuted,
        )
        else -> {
            val viaWallet = !state.deviceLinked || phase == VaultPhase.Expired
            HoldToConfirm(
                label = when {
                    phase == VaultPhase.Expired -> "Hold to revive with wallet"
                    viaWallet -> "Hold to clock in with wallet"
                    else -> "Hold to clock in"
                },
                onProgress = { hold = it },
                onConfirmed = { viewModel.checkIn(activity, sender) },
                loading = state.busy == Busy.CheckingIn,
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (viaWallet) Icons.Shield else Icons.Fingerprint, null, tint = Vigil.Faint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (viaWallet) "Signed by your wallet" else "Signed on this phone · no wallet pop-up",
                    style = Type.Label,
                    color = Vigil.Faint,
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Panel {
        Row {
            Stat("Streak", "${vault.streak}d", accent = Vigil.Wick)
            Stat("Best", "${vault.bestStreak}d")
            Stat("Check-ins", "${vault.totalPulses}")
        }
    }

    Spacer(Modifier.height(12.dp))
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("If you go quiet", modifier = Modifier.weight(1f))
            Text(TimeText.dateTime(vault.deadline), style = Type.Label, color = Vigil.Muted)
        }
        Spacer(Modifier.height(14.dp))
        vault.heirs.forEachIndexed { i, heir ->
            val name = viewModel.heirLabel(heir.wallet) ?: heir.wallet.short()
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                HeirAvatar(name, i, 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(name, style = Type.Body, modifier = Modifier.weight(1f))
                Text("${heir.shareBps / 100.0}".removeSuffix(".0") + "%", style = Type.Numeric)
            }
        }
    }

    if (state.activity.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Panel {
            Overline("On-chain log")
            Spacer(Modifier.height(8.dp))
            state.activity.take(6).forEachIndexed { i, entry ->
                if (i > 0) Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl(entry.signature)))) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp)) {
                        app.vigil.ui.components.StatusDot(if (entry.failed) Vigil.Blood else Vigil.Flame)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(entry.signature.take(8) + "…" + entry.signature.takeLast(6), style = Type.Numeric.copy(fontSize = Type.Label.fontSize), modifier = Modifier.weight(1f))
                    Text(entry.blockTime?.let { TimeText.relative(it, now) } ?: "pending", style = Type.Label)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.External, null, tint = Vigil.Faint, modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    if (!state.deviceLinked && phase != VaultPhase.Released) {
        Spacer(Modifier.height(12.dp))
        Panel {
            Text("This phone isn't linked yet", style = Type.Headline)
            Spacer(Modifier.height(6.dp))
            Text("Link it once with your wallet, and daily check-ins won't need the wallet again.", style = Type.BodyMuted)
            Spacer(Modifier.height(14.dp))
            VigilButton("Link this phone", onClick = { viewModel.linkThisPhone(sender) }, kind = ButtonKind.Outline, loading = state.busy == Busy.Rotating)
        }
    }
}
