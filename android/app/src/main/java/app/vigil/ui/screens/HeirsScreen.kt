package app.vigil.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vigil.ui.VigilState
import app.vigil.ui.VigilViewModel
import app.vigil.ui.components.ButtonKind
import app.vigil.ui.components.Divider
import app.vigil.ui.components.HeirAvatar
import app.vigil.ui.components.KeyValue
import app.vigil.ui.components.Overline
import app.vigil.ui.components.Panel
import app.vigil.ui.components.SplitBar
import app.vigil.ui.components.TimeText
import app.vigil.ui.components.VigilButton
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil

fun shareText(bps: Int): String = "${bps / 100.0}".removeSuffix(".0") + "%"

@Composable
fun HeirsScreen(state: VigilState, viewModel: VigilViewModel, onLight: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val vault = state.vault

    TabPage(title = "Heirs", kicker = "Who it goes to", onSettings = onSettings) {
        if (vault == null) {
            Panel {
                Text("Nobody yet", style = Type.Headline)
                Spacer(Modifier.height(6.dp))
                Text("Up to five people or backup wallets, each with a share. You can change them whenever you like.", style = Type.BodyMuted)
                Spacer(Modifier.height(16.dp))
                VigilButton("Light your Vigil", onClick = onLight)
            }
            return@TabPage
        }

        SplitBar(vault.heirs.map { it.shareBps })
        Spacer(Modifier.height(18.dp))

        Panel {
            vault.heirs.forEachIndexed { i, heir ->
                if (i > 0) Divider()
                val name = viewModel.heirLabel(heir.wallet)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeirAvatar(name ?: "?", i, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name ?: "Unnamed heir", style = Type.Headline)
                        AddressText(heir.wallet)
                    }
                    Text(shareText(heir.shareBps), style = Type.Title, color = Vigil.HeirPalette[i % Vigil.HeirPalette.size])
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Panel {
            Overline("The rule")
            Spacer(Modifier.height(10.dp))
            Text(
                "If you don't clock in ${TimeText.cadence(vault.interval)}, a grace period of ${TimeText.span(vault.grace)} starts. " +
                    "If that passes too, anyone can trigger the release, and the program splits the vault by these shares.",
                style = Type.Body,
            )
            Spacer(Modifier.height(8.dp))
            KeyValue("Check-in window", TimeText.span(vault.interval))
            Divider()
            KeyValue("Grace period", if (vault.grace == 0L) "None" else TimeText.span(vault.grace))
            Divider()
            KeyValue("Release possible after", TimeText.dateTime(vault.deadline))
        }

        Spacer(Modifier.height(18.dp))
        if (!vault.isReleased) {
            VigilButton("Edit heirs & schedule", onClick = onEdit, kind = ButtonKind.Solid, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        VigilButton(
            "Tell my heirs",
            kind = ButtonKind.Outline,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val text = "I've named you in my Vigil, a crypto will on Solana. If I ever stop checking in, " +
                    "open Vigil on Android with your wallet to claim your share. Vault: ${vault.address.toBase58()}"
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Tell your heirs"),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        Text("Names stay on this phone. Only wallet addresses and shares are stored on-chain.", style = Type.Label, color = Vigil.Faint)
    }
}
