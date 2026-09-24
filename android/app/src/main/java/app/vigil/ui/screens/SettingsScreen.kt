package app.vigil.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vigil.BuildConfig
import app.vigil.data.formatSol
import app.vigil.solana.VigilProgram
import app.vigil.ui.Busy
import app.vigil.ui.LocalSender
import app.vigil.ui.VigilState
import app.vigil.ui.VigilViewModel
import app.vigil.ui.components.ButtonKind
import app.vigil.ui.components.Divider
import app.vigil.ui.components.Icons
import app.vigil.ui.components.Overline
import app.vigil.ui.components.Panel
import app.vigil.ui.components.Pill
import app.vigil.ui.components.VigilButton
import app.vigil.ui.explorerUrl
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import app.vigil.widget.VigilWidgetReceiver

@Composable
fun SettingsScreen(state: VigilState, viewModel: VigilViewModel, onClose: () -> Unit) {
    val sender = LocalSender.current
    val context = LocalContext.current
    var confirmClose by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setReminders(granted)
    }

    FullPage("Settings", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))

            Overline("Wallet")
            Spacer(Modifier.height(8.dp))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.wallet?.label ?: "Connected wallet", style = Type.Headline)
                        state.wallet?.publicKey?.let { AddressText(it, head = 6, tail = 6) }
                    }
                    Pill(BuildConfig.CLUSTER.replaceFirstChar { it.uppercase() }, if (VigilViewModel.isDevnet) Vigil.Flame else Vigil.Moss, dot = true)
                }
                Spacer(Modifier.height(10.dp))
                val sol = state.walletAssets.firstOrNull { it.isSol }?.amount ?: 0
                Text("${formatSol(sol)} SOL available", style = Type.Label)
                if (VigilViewModel.isDevnet) {
                    Spacer(Modifier.height(14.dp))
                    VigilButton(
                        "Get 1 devnet SOL",
                        onClick = viewModel::airdrop,
                        kind = ButtonKind.Ghost,
                        loading = state.busy == Busy.Airdropping,
                        height = 46.dp,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Overline("This phone")
            Spacer(Modifier.height(8.dp))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Fingerprint, null, tint = Vigil.Flame, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pulse key", style = Type.Headline)
                        Text(
                            when {
                                state.vault == null -> "Created when you light your Vigil"
                                state.deviceLinked -> "Linked. Can check in, can never move funds."
                                else -> "Not linked to your vault"
                            },
                            style = Type.Label,
                        )
                    }
                }
                state.pulseKey?.let {
                    Spacer(Modifier.height(12.dp))
                    AddressText(it, head = 6, tail = 6)
                }
                if (state.vault != null) {
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fuel", style = Type.Body)
                            Text("${formatSol(state.pulseKeyLamports, 5)} SOL · about ${state.pulseFuel} check-ins", style = Type.Label)
                        }
                        if (state.deviceLinked) {
                            VigilButton("Top up", onClick = { viewModel.refuel(sender) }, kind = ButtonKind.Ghost, height = 40.dp, loading = state.busy == Busy.Refueling)
                        }
                    }
                    Divider()
                    Spacer(Modifier.height(12.dp))
                    VigilButton(
                        if (state.deviceLinked) "Replace pulse key" else "Link this phone",
                        onClick = { viewModel.linkThisPhone(sender) },
                        kind = ButtonKind.Outline,
                        height = 46.dp,
                        loading = state.busy == Busy.Rotating,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Use this after a new phone or a reinstall. The old key stops working immediately.", style = Type.Label, color = Vigil.Faint)
                }
            }

            Spacer(Modifier.height(22.dp))
            Overline("Staying on time")
            Spacer(Modifier.height(8.dp))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminders", style = Type.Headline)
                        Text("A nudge when a quarter of your window is left, and again in the grace period.", style = Type.Label)
                    }
                    Switch(
                        checked = state.remindersEnabled,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else viewModel.setReminders(on)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Vigil.Ink, checkedTrackColor = Vigil.Flame, uncheckedTrackColor = Vigil.Raised),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Divider()
                SettingRow(Icons.Pulse, "Add home screen widget", "Countdown and one-tap check-in") {
                    val manager = context.getSystemService(AppWidgetManager::class.java)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(ComponentName(context, VigilWidgetReceiver::class.java), null, null)
                    }
                }
                Divider()
                Text(
                    "Tip: add the Vigil tile to Quick Settings. Swipe down, tap, touch the sensor. Done.",
                    style = Type.Label,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(22.dp))
            Overline("About")
            Spacer(Modifier.height(8.dp))
            Panel {
                SettingRow(Icons.Shield, "Vigil program", VigilProgram.ID.short(6, 6)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl(VigilProgram.ID.toBase58(), isTx = false))))
                }
                Divider()
                SettingRow(Icons.External, "Source code", "github.com/victorhez/vigil") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/victorhez/vigil")))
                }
                Divider()
                Text("Version ${BuildConfig.VERSION_NAME}", style = Type.Label, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(22.dp))
            if (state.vault != null && !state.vault.isReleased) {
                VigilButton("Close vault", onClick = { confirmClose = true }, kind = ButtonKind.Danger, modifier = Modifier.fillMaxWidth(), loading = state.busy == Busy.Closing)
                Spacer(Modifier.height(10.dp))
            }
            VigilButton("Disconnect wallet", onClick = { viewModel.disconnect(sender); onClose() }, kind = ButtonKind.Ghost, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            containerColor = Vigil.Raised,
            title = { Text("Close your vault?", style = Type.Title) },
            text = { Text("Everything in it returns to your wallet and your heirs will no longer be covered.", style = Type.BodyMuted) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; viewModel.closeVault(sender) }) { Text("Close vault", color = Vigil.Blood, style = Type.Button) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Keep it", color = Vigil.Bone, style = Type.Button) }
            },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Vigil.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            Text(value, style = Type.Label)
        }
        Icon(Icons.Arrow, null, tint = Vigil.Faint, modifier = Modifier.size(16.dp))
    }
}
