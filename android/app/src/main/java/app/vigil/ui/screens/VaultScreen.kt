package app.vigil.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.vigil.data.Asset
import app.vigil.data.KnownTokens
import app.vigil.solana.VaultPhase
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

private enum class Sheet { Deposit, Withdraw }

@Composable
fun VaultScreen(state: VigilState, viewModel: VigilViewModel, onLight: () -> Unit, onSettings: () -> Unit) {
    val sender = LocalSender.current
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var preset by remember { mutableStateOf<Asset?>(null) }
    val vault = state.vault

    // Close the sheet once the transaction has gone through.
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) {
        val moving = state.busy == Busy.Depositing || state.busy == Busy.Withdrawing
        if (wasBusy && !moving) sheet = null
        wasBusy = moving
    }

    TabPage(title = "Vault", kicker = "What you're protecting", onSettings = onSettings) {
        if (vault == null) {
            Panel {
                Text("No vault yet", style = Type.Headline)
                Spacer(Modifier.height(6.dp))
                Text("Light your Vigil to open a vault only you can withdraw from, and only your heirs can inherit.", style = Type.BodyMuted)
                Spacer(Modifier.height(16.dp))
                VigilButton("Light your Vigil", onClick = onLight)
            }
            return@TabPage
        }

        val sol = state.vaultAssets.firstOrNull { it.isSol }
        val tokens = state.vaultAssets.filter { !it.isSol }
        val sealed = vault.isReleased

        Text(
            buildAnnotatedString {
                append(sol?.formatted(4) ?: "0")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Vigil.Muted)) { append(" SOL") }
            },
            style = Type.Hero,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (tokens.isEmpty()) "No tokens yet" else "plus ${tokens.size} token${if (tokens.size == 1) "" else "s"}",
            style = Type.BodyMuted,
        )

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VigilButton("Deposit", onClick = { preset = null; sheet = Sheet.Deposit }, modifier = Modifier.weight(1f), enabled = !sealed)
            VigilButton("Withdraw", onClick = { preset = null; sheet = Sheet.Withdraw }, kind = ButtonKind.Outline, modifier = Modifier.weight(1f), enabled = !sealed)
        }
        Spacer(Modifier.height(10.dp))
        Text("Only your wallet can withdraw. Each deposit or withdrawal also counts as a check-in.", style = Type.Label, color = Vigil.Faint)

        val walletSkr = state.walletAssets.firstOrNull { it.mint == KnownTokens.SKR }
        if (walletSkr != null && tokens.none { it.mint == KnownTokens.SKR } && !sealed) {
            Spacer(Modifier.height(18.dp))
            Panel(onClick = { preset = walletSkr; sheet = Sheet.Deposit }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TokenGlyph(walletSkr)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Protect your SKR", style = Type.Headline)
                        Text("${walletSkr.formatted(2)} SKR in your wallet isn't covered yet.", style = Type.BodyMuted)
                    }
                    Icon(Icons.Arrow, null, tint = Vigil.Flame, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Panel {
            Overline("Holdings")
            Spacer(Modifier.height(6.dp))
            state.vaultAssets.forEachIndexed { i, asset ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TokenGlyph(asset)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(asset.symbol, style = Type.Headline)
                            if (asset.featured) {
                                Spacer(Modifier.width(8.dp))
                                Pill("Seeker", Vigil.Flame)
                            }
                        }
                        Text(asset.name, style = Type.Label)
                    }
                    Text(asset.formatted(4), style = Type.Numeric)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Panel(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl(vault.address.toBase58(), isTx = false)))) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Vault account")
                    Spacer(Modifier.height(6.dp))
                    AddressText(vault.address, head = 8, tail = 8)
                }
                Icon(Icons.External, "Open in explorer", tint = Vigil.Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (vault.phase(System.currentTimeMillis() / 1000)) {
                    VaultPhase.Released -> "Released to heirs. The vault is sealed."
                    else -> "A program-owned account. No one, including Vigil, can move these funds except you, or your heirs after release."
                },
                style = Type.Label,
            )
        }
    }

    when (sheet) {
        Sheet.Deposit -> AssetSheet(
            title = "Deposit to vault",
            action = "Deposit",
            assets = state.walletAssets.filter { it.amount > 0 },
            reserveSolForFees = true,
            busy = state.busy == Busy.Depositing,
            initial = preset,
            onDismiss = { sheet = null },
            onConfirm = { asset, amount -> viewModel.deposit(sender, asset, amount) },
        )
        Sheet.Withdraw -> AssetSheet(
            title = "Withdraw to wallet",
            action = "Withdraw",
            assets = state.vaultAssets.filter { it.amount > 0 },
            reserveSolForFees = false,
            busy = state.busy == Busy.Withdrawing,
            onDismiss = { sheet = null },
            onConfirm = { asset, amount -> viewModel.withdraw(sender, asset, amount) },
        )
        null -> Unit
    }
}
