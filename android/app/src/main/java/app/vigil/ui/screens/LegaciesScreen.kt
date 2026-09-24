package app.vigil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.vigil.data.formatAmount
import app.vigil.solana.VaultAccount
import app.vigil.solana.VaultPhase
import app.vigil.ui.Busy
import app.vigil.ui.LocalSender
import app.vigil.ui.VigilState
import app.vigil.ui.VigilViewModel
import app.vigil.ui.components.Divider
import app.vigil.ui.components.Overline
import app.vigil.ui.components.Panel
import app.vigil.ui.components.Pill
import app.vigil.ui.components.PulseDial
import app.vigil.ui.components.TimeText
import app.vigil.ui.components.VigilButton
import app.vigil.ui.components.rememberNow
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import java.math.BigDecimal

@Composable
fun LegaciesScreen(state: VigilState, viewModel: VigilViewModel, onSettings: () -> Unit) {
    val now = rememberNow()
    TabPage(title = "Legacies", kicker = "Vigils that name you", onSettings = onSettings) {
        if (state.legacies.isEmpty()) {
            Panel {
                Text("No one has named you yet", style = Type.Headline)
                Spacer(Modifier.height(6.dp))
                Text(
                    "When someone adds this wallet as an heir, their vault appears here. You'll see whether they're still checking in, " +
                        "and if they stop, you can release it.",
                    style = Type.BodyMuted,
                )
            }
            return@TabPage
        }
        state.legacies
            .sortedBy { it.deadline }
            .forEach { vault ->
                LegacyCard(state, vault, now, viewModel)
                Spacer(Modifier.height(12.dp))
            }
    }
}

@Composable
private fun LegacyCard(state: VigilState, vault: VaultAccount, now: Long, viewModel: VigilViewModel) {
    val sender = LocalSender.current
    val me = state.wallet?.publicKey
    val phase = vault.phase(now)
    val myShare = vault.heirs.firstOrNull { it.wallet == me }?.shareBps ?: 0
    val assets = state.legacyAssets[vault.address].orEmpty().filter { it.amount > 0 }
    val claimable = assets.isNotEmpty()

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDial(
                remaining = when (phase) {
                    VaultPhase.Alive -> (vault.dueAt - now) / vault.interval.toFloat()
                    VaultPhase.Overdue -> if (vault.grace > 0) (vault.deadline - now) / vault.grace.toFloat() else 0f
                    else -> 0f
                },
                phase = phase,
                hold = 0f,
                burstKey = null,
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(viewModel.heirLabel(vault.owner) ?: "From ${vault.owner.short()}", style = Type.Headline)
                Spacer(Modifier.height(2.dp))
                Text("Your share · ${shareText(myShare)}", style = Type.Label)
            }
            val (label, color) = when (phase) {
                VaultPhase.Alive -> "Checking in" to Vigil.Moss
                VaultPhase.Overdue -> "Overdue" to Vigil.Ember
                VaultPhase.Expired -> "Claimable" to Vigil.Blood
                VaultPhase.Released -> "Released" to Vigil.Ash
            }
            Pill(label, color, dot = true)
        }

        Spacer(Modifier.height(14.dp))
        Divider()
        Spacer(Modifier.height(10.dp))

        when (phase) {
            VaultPhase.Alive, VaultPhase.Overdue -> {
                Text(
                    "Last check-in ${TimeText.relative(vault.lastPulse, now)} · streak ${vault.streak}d",
                    style = Type.BodyMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Release opens in ${TimeText.clock(vault.deadline - now)}",
                    style = Type.Numeric.copy(fontSize = Type.Label.fontSize),
                    color = if (phase == VaultPhase.Overdue) Vigil.Ember else Vigil.Muted,
                )
            }
            VaultPhase.Expired, VaultPhase.Released -> {
                if (claimable) {
                    Overline("Waiting to be released")
                    Spacer(Modifier.height(8.dp))
                    assets.forEach { asset ->
                        val mine = asset.uiAmount.multiply(BigDecimal(myShare)).divide(BigDecimal(10_000))
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(asset.symbol, style = Type.Body)
                            Text("${formatAmount(mine, 4)} of ${asset.formatted(4)}", style = Type.Numeric.copy(fontSize = Type.Label.fontSize))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    VigilButton(
                        "Release to all heirs",
                        onClick = { viewModel.release(sender, vault) },
                        loading = state.busy == Busy.Releasing,
                        enabled = state.busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every heir gets their share in the same transaction. You only pay the network fee.",
                        style = Type.Label,
                        color = Vigil.Faint,
                    )
                } else {
                    Text(
                        if (vault.isReleased) "Released on ${TimeText.date(vault.releasedAt)}." else "The window closed, but the vault is empty.",
                        style = Type.BodyMuted.copy(fontStyle = FontStyle.Italic),
                    )
                }
            }
        }
    }
}
