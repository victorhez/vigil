package app.vigil.ui.screens

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vigil.data.Asset
import app.vigil.data.formatSol
import app.vigil.solana.PublicKey
import app.vigil.solana.VigilProgram
import app.vigil.ui.Busy
import app.vigil.ui.HeirDraft
import app.vigil.ui.LocalSender
import app.vigil.ui.PULSE_FUEL_LAMPORTS
import app.vigil.ui.VigilPlan
import app.vigil.ui.VigilState
import app.vigil.ui.VigilViewModel
import app.vigil.ui.components.ButtonKind
import app.vigil.ui.components.ChoiceChip
import app.vigil.ui.components.Divider
import app.vigil.ui.components.HeirAvatar
import app.vigil.ui.components.Icons
import app.vigil.ui.components.Overline
import app.vigil.ui.components.Panel
import app.vigil.ui.components.Pill
import app.vigil.ui.components.SplitBar
import app.vigil.ui.components.TimeText
import app.vigil.ui.components.TimeText.DAY
import app.vigil.ui.components.TimeText.HOUR
import app.vigil.ui.components.TimeText.MINUTE
import app.vigil.ui.components.VigilButton
import app.vigil.ui.theme.Mono
import app.vigil.ui.theme.Sans
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private data class Choice(val seconds: Long, val title: String, val sub: String)

private val intervals = listOf(
    Choice(DAY, "Daily", "The ritual"),
    Choice(3 * DAY, "Every 3 days", "Relaxed"),
    Choice(7 * DAY, "Weekly", "Sunday check-in"),
    Choice(30 * DAY, "Monthly", "Light touch"),
)
private val demoInterval = Choice(2 * MINUTE, "2 minutes", "Demo · devnet")

private val graces = listOf(
    Choice(0, "None", "Strict"),
    Choice(HOUR, "1 hour", "Tight"),
    Choice(DAY, "1 day", "Recommended"),
    Choice(7 * DAY, "7 days", "Travel-proof"),
    Choice(30 * DAY, "30 days", "Very forgiving"),
)
private val demoGrace = Choice(MINUTE, "1 minute", "Demo · devnet")

private enum class Step(val title: String) { Rhythm("Rhythm"), Heirs("Heirs"), Fund("Fund"), Review("Review") }

@Composable
fun SetupScreen(state: VigilState, viewModel: VigilViewModel, editing: Boolean, onClose: () -> Unit) {
    val sender = LocalSender.current
    val existing = state.vault.takeIf { editing }
    val steps = if (editing) listOf(Step.Rhythm, Step.Heirs, Step.Review) else Step.entries.toList()
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps[stepIndex]

    var interval by remember { mutableLongStateOf(existing?.interval ?: DAY) }
    var grace by remember { mutableLongStateOf(existing?.grace ?: DAY) }
    val heirs = remember {
        mutableStateListOf<HeirDraft>().apply {
            if (existing != null) {
                existing.heirs.forEach { add(HeirDraft(it.wallet.toBase58(), viewModel.heirLabel(it.wallet) ?: "", it.shareBps)) }
            } else {
                add(HeirDraft(shareBps = 10_000))
            }
        }
    }
    var solText by remember { mutableStateOf("") }
    val tokenPicks = remember { mutableStateMapOf<PublicKey, Boolean>() }

    // Leave once the chain reflects the change.
    val startPulse = remember { existing?.lastPulse }
    LaunchedEffect(state.vault?.lastPulse, state.vault != null) {
        if (!editing && state.vault != null) onClose()
        if (editing && startPulse != null && (state.vault?.lastPulse ?: 0) > startPulse) onClose()
    }

    val owner = state.wallet?.publicKey
    val heirsValid = heirs.isNotEmpty() && heirs.size <= VigilProgram.MAX_HEIRS &&
        heirs.all { it.publicKey != null && it.shareBps > 0 } &&
        heirs.mapNotNull { it.publicKey }.toSet().size == heirs.size &&
        heirs.sumOf { it.shareBps } == 10_000 &&
        heirs.none { owner != null && it.publicKey == VigilProgram.vaultAddress(owner) }

    val walletSol = state.walletAssets.firstOrNull { it.isSol }?.amount ?: 0L
    val solLamports = solText.toBigDecimalOrNull()?.movePointRight(9)?.toLong() ?: 0L
    val tokens = state.walletAssets.filter { !it.isSol && it.amount > 0 }
    val fundValid = solLamports >= 0 && solLamports + PULSE_FUEL_LAMPORTS + 10_000_000 <= walletSol

    val canContinue = when (step) {
        Step.Rhythm -> true
        Step.Heirs -> heirsValid
        Step.Fund -> fundValid
        Step.Review -> heirsValid
    }

    FullPage(
        title = if (editing) "Edit your Vigil" else "Light your Vigil",
        onClose = { if (stepIndex == 0) onClose() else stepIndex-- },
        closeIcon = if (stepIndex == 0) Icons.Close else Icons.Back,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
            steps.forEachIndexed { i, s ->
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
                            .background(if (i <= stepIndex) Vigil.Flame else Vigil.Line),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(s.title, style = Type.Overline, color = if (i == stepIndex) Vigil.Bone else Vigil.Faint)
                }
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { (slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(320))) togetherWith fadeOut(tween(160)) },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
                when (current) {
                    Step.Rhythm -> RhythmStep(interval, grace, onInterval = { interval = it }, onGrace = { grace = it })
                    Step.Heirs -> HeirsStep(heirs, owner)
                    Step.Fund -> FundStep(solText, { solText = it }, walletSol, tokens, tokenPicks)
                    Step.Review -> ReviewStep(interval, grace, heirs, solLamports, tokens.filter { tokenPicks[it.mint] == true }, editing)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            if (step == Step.Review) {
                VigilButton(
                    text = if (editing) "Save with wallet" else "Light it",
                    loading = state.busy == Busy.Lighting || state.busy == Busy.Saving,
                    enabled = canContinue && state.busy == null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (editing) {
                            viewModel.saveConfig(sender, interval, grace, heirs.toList())
                        } else {
                            viewModel.light(
                                sender,
                                VigilPlan(
                                    interval = interval,
                                    grace = grace,
                                    heirs = heirs.toList(),
                                    solLamports = solLamports,
                                    tokens = tokens.filter { tokenPicks[it.mint] == true }.map { it to it.amount },
                                ),
                            )
                        }
                    },
                )
            } else {
                VigilButton("Continue", onClick = { stepIndex++ }, enabled = canContinue, kind = ButtonKind.Solid, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RhythmStep(interval: Long, grace: Long, onInterval: (Long) -> Unit, onGrace: (Long) -> Unit) {
    Text(
        buildAnnotatedString {
            append("How often will you ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Vigil.Wick)) { append("clock in?") }
        },
        style = Type.Display,
    )
    Spacer(Modifier.height(8.dp))
    Text("Pick something you'll actually keep. Every check-in is one hold and a fingerprint.", style = Type.BodyMuted)
    Spacer(Modifier.height(20.dp))
    val intervalChoices = if (VigilViewModelDevnet) intervals + demoInterval else intervals
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        intervalChoices.forEach { c -> ChoiceChip(c.title, interval == c.seconds, { onInterval(c.seconds) }, sub = c.sub) }
    }

    Spacer(Modifier.height(30.dp))
    Overline("Grace period")
    Spacer(Modifier.height(8.dp))
    Text("Extra time after a missed check-in before your heirs can claim. Reminders go out before it starts.", style = Type.BodyMuted)
    Spacer(Modifier.height(16.dp))
    val graceChoices = if (VigilViewModelDevnet) graces + demoGrace else graces
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        graceChoices.forEach { c -> ChoiceChip(c.title, grace == c.seconds, { onGrace(c.seconds) }, sub = c.sub.ifEmpty { null }) }
    }
}

private val VigilViewModelDevnet: Boolean get() = VigilViewModel.isDevnet

@Composable
private fun HeirsStep(heirs: MutableList<HeirDraft>, owner: PublicKey?) {
    val context = LocalContext.current
    var scanTarget by remember { mutableIntStateOf(-1) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        val address = raw.removePrefix("solana:").substringBefore("?").trim()
        if (scanTarget in heirs.indices) heirs[scanTarget] = heirs[scanTarget].copy(address = address)
    }

    Text(
        buildAnnotatedString {
            append("Who should ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Vigil.Wick)) { append("inherit?") }
        },
        style = Type.Display,
    )
    Spacer(Modifier.height(8.dp))
    Text("Family, a friend, or your own backup wallet. Up to five, each with a share.", style = Type.BodyMuted)
    Spacer(Modifier.height(18.dp))

    SplitBar(heirs.map { it.shareBps })
    Spacer(Modifier.height(8.dp))
    val total = heirs.sumOf { it.shareBps }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${shareText(total)} allocated",
            style = Type.Label,
            color = if (total == 10_000) Vigil.Moss else Vigil.Ember,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Split evenly",
            style = Type.Label,
            color = Vigil.Flame,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { splitEvenly(heirs) }.padding(6.dp),
        )
    }
    Spacer(Modifier.height(12.dp))

    heirs.forEachIndexed { i, heir ->
        val key = heir.publicKey
        val problem = when {
            heir.address.isBlank() -> null
            key == null -> "Not a valid Solana address"
            owner != null && key == VigilProgram.vaultAddress(owner) -> "That's the vault itself"
            heirs.count { it.publicKey == key } > 1 -> "Listed twice"
            else -> null
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeirAvatar(heir.label.ifBlank { "${i + 1}" }, i, 36.dp)
                Spacer(Modifier.width(12.dp))
                PlainField(
                    value = heir.label,
                    onChange = { heirs[i] = heir.copy(label = it.take(28)) },
                    placeholder = "Name (only on this phone)",
                    style = Type.Headline,
                    modifier = Modifier.weight(1f),
                    capitalize = true,
                )
                if (heirs.size > 1) {
                    Icon(
                        Icons.Close, "Remove heir", tint = Vigil.Faint,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { heirs.removeAt(i) }.padding(6.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Vigil.Raised)
                    .border(1.dp, if (problem != null) Vigil.Blood.copy(alpha = 0.6f) else Vigil.Line, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlainField(
                    value = heir.address,
                    onChange = { heirs[i] = heir.copy(address = it.trim()) },
                    placeholder = "Wallet address",
                    style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Vigil.Bone),
                    modifier = Modifier.weight(1f),
                )
                SmallAction("Paste") {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text != null) heirs[i] = heir.copy(address = text.trim())
                }
                Icon(
                    Icons.Scan, "Scan QR", tint = Vigil.Bone,
                    modifier = Modifier.size(38.dp).clip(CircleShape).clickable {
                        scanTarget = i
                        scanner.launch(ScanOptions().setPrompt("Scan a Solana address").setBeepEnabled(false).setOrientationLocked(true))
                    }.padding(9.dp),
                )
            }
            if (problem != null) {
                Spacer(Modifier.height(6.dp))
                Text(problem, style = Type.Label, color = Vigil.Blood)
            }
            if (owner != null && heir.address.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use my backup wallet instead",
                    style = Type.Label,
                    color = Vigil.Faint,
                    modifier = Modifier.clickable { heirs[i] = heir.copy(address = owner.toBase58(), label = heir.label.ifBlank { "Me (recovery)" }) },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Share", modifier = Modifier.weight(1f))
                Stepper("−") { heirs[i] = heir.copy(shareBps = (heir.shareBps - 500).coerceAtLeast(0)) }
                Text(
                    shareText(heir.shareBps),
                    style = Type.Numeric.copy(fontSize = 18.sp),
                    color = Vigil.HeirPalette[i % Vigil.HeirPalette.size],
                    modifier = Modifier.width(76.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Stepper("+") { heirs[i] = heir.copy(shareBps = (heir.shareBps + 500).coerceAtMost(10_000)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (heirs.size < VigilProgram.MAX_HEIRS) {
        VigilButton(
            "Add another heir",
            kind = ButtonKind.Outline,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                heirs.add(HeirDraft())
                splitEvenly(heirs)
            },
        )
    }
    if (owner != null && heirs.any { it.publicKey == owner }) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Naming your own wallet turns Vigil into a recovery switch: if you lose this phone and stop checking in, funds return to you.",
            style = Type.Label,
        )
    }
}

private fun splitEvenly(heirs: MutableList<HeirDraft>) {
    if (heirs.isEmpty()) return
    val each = 10_000 / heirs.size
    heirs.indices.forEach { i ->
        heirs[i] = heirs[i].copy(shareBps = if (i == heirs.lastIndex) 10_000 - each * (heirs.size - 1) else each)
    }
}

@Composable
private fun FundStep(
    solText: String,
    onSol: (String) -> Unit,
    walletSol: Long,
    tokens: List<Asset>,
    picks: MutableMap<PublicKey, Boolean>,
) {
    Text(
        buildAnnotatedString {
            append("What should it ")
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Vigil.Wick)) { append("protect?") }
        },
        style = Type.Display,
    )
    Spacer(Modifier.height(8.dp))
    Text("You can add more or take it back any time. Start small if you like.", style = Type.BodyMuted)
    Spacer(Modifier.height(22.dp))

    Panel {
        Overline("SOL")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            PlainField(
                value = solText,
                onChange = { v -> if (v.length <= 14 && v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) onSol(v) },
                placeholder = "0",
                style = Type.Display.copy(fontFamily = Mono, fontSize = 36.sp),
                modifier = Modifier.weight(1f),
                keyboard = KeyboardType.Decimal,
            )
            Text("SOL", style = Type.Headline, color = Vigil.Muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("${formatSol(walletSol)} SOL in wallet", style = Type.Label)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0.1", "0.5", "1").forEach { v -> SmallAction("$v SOL") { onSol(v) } }
        }
    }

    if (tokens.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Panel {
            Overline("Tokens")
            Spacer(Modifier.height(4.dp))
            tokens.forEachIndexed { i, asset ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TokenGlyph(asset, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(asset.symbol, style = Type.Headline)
                            if (asset.featured) {
                                Spacer(Modifier.width(8.dp))
                                Pill("Seeker", Vigil.Flame)
                            }
                        }
                        Text("All ${asset.formatted(4)}", style = Type.Label)
                    }
                    Switch(
                        checked = picks[asset.mint] == true,
                        onCheckedChange = { picks[asset.mint!!] = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Vigil.Ink,
                            checkedTrackColor = Vigil.Flame,
                            uncheckedThumbColor = Vigil.Muted,
                            uncheckedTrackColor = Vigil.Raised,
                            uncheckedBorderColor = Vigil.LineStrong,
                        ),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Row {
        Icon(Icons.Fingerprint, null, tint = Vigil.Faint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "0.01 SOL also goes to this phone's pulse key to pay check-in fees. That's about 2,000 check-ins.",
            style = Type.Label,
        )
    }
}

@Composable
private fun ReviewStep(
    interval: Long,
    grace: Long,
    heirs: List<HeirDraft>,
    solLamports: Long,
    tokens: List<Asset>,
    editing: Boolean,
) {
    val holdings = buildList {
        if (solLamports > 0) add("${formatSol(solLamports)} SOL")
        tokens.forEach { add("${it.formatted(2)} ${it.symbol}") }
    }
    val what = when {
        editing -> "everything in your vault"
        holdings.isEmpty() -> "whatever you deposit"
        else -> holdings.joinToString(" and ")
    }
    val who = heirs.joinToString(", ") { "${it.label.ifBlank { it.publicKey?.short() ?: "?" }} (${shareText(it.shareBps)})" }
    val window = TimeText.span(interval) + if (grace > 0) " plus ${TimeText.span(grace)} of grace" else ""

    Overline("Your Vigil, in one sentence")
    Spacer(Modifier.height(14.dp))
    Text(
        buildAnnotatedString {
            append("If I go quiet for ")
            withStyle(SpanStyle(color = Vigil.Wick, fontStyle = FontStyle.Italic)) { append(window) }
            append(", ")
            withStyle(SpanStyle(color = Vigil.Wick, fontStyle = FontStyle.Italic)) { append(what) }
            append(" goes to ")
            withStyle(SpanStyle(color = Vigil.Wick, fontStyle = FontStyle.Italic)) { append(who) }
            append(".")
        },
        style = Type.Title.copy(lineHeight = 36.sp),
    )
    Spacer(Modifier.height(24.dp))
    Panel {
        heirs.forEachIndexed { i, h ->
            if (i > 0) Divider()
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                HeirAvatar(h.label.ifBlank { "${i + 1}" }, i, 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.label.ifBlank { "Unnamed" }, style = Type.Body)
                    Text(h.publicKey?.short(6, 6) ?: "", style = Type.Numeric.copy(fontSize = 12.sp, color = Vigil.Faint))
                }
                Text(shareText(h.shareBps), style = Type.Numeric)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        if (editing) "Your wallet signs one transaction. Saving also counts as a check-in."
        else "Your wallet signs once. After that, daily check-ins happen on this phone with no wallet pop-up.",
        style = Type.Label,
    )
}

@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style.copy(color = Vigil.Bone),
        singleLine = true,
        cursorBrush = SolidColor(Vigil.Flame),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        modifier = modifier.padding(vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Vigil.Faint))
                inner()
            }
        },
    )
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Vigil.Ink.copy(alpha = 0.5f)).border(1.dp, Vigil.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, style = Type.Label.copy(fontFamily = Sans), color = Vigil.Bone)
    }
}

@Composable
private fun Stepper(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Vigil.Raised).border(1.dp, Vigil.Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = Type.Headline)
    }
}
