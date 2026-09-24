package app.vigil.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import kotlinx.coroutines.launch

enum class ButtonKind { Flame, Solid, Outline, Ghost, Danger }

@Composable
fun VigilButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Flame,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val shape = RoundedCornerShape(percent = 50)
    val active = enabled && !loading

    val (background, content, border) = when (kind) {
        ButtonKind.Flame -> Triple(Vigil.FlameGradient, Vigil.Ink, null)
        ButtonKind.Solid -> Triple(Brush.linearGradient(listOf(Vigil.Bone, Vigil.Bone)), Vigil.Ink, null)
        ButtonKind.Outline -> Triple(Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), Vigil.Bone, BorderStroke(1.dp, Vigil.LineStrong))
        ButtonKind.Ghost -> Triple(Brush.linearGradient(listOf(Vigil.Raised, Vigil.Raised)), Vigil.Bone, null)
        ButtonKind.Danger -> Triple(Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), Vigil.Blood, BorderStroke(1.dp, Vigil.Blood.copy(alpha = 0.5f)))
    }

    Box(
        modifier
            .scale(scale)
            .height(height)
            .clip(shape)
            .background(background, alpha = if (active) 1f else 0.35f)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            Text(text, style = Type.Button, color = content.copy(alpha = if (enabled) 1f else 0.6f))
        }
    }
}

/**
 * Press and hold to confirm. Deliberate by design: a check-in should be a small ritual, not a mis-tap.
 * Reports progress so the dial can draw the ring filling up.
 */
@Composable
fun HoldToConfirm(
    label: String,
    onProgress: (Float) -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    durationMs: Int = 1100,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (progress.value > 0f) 0.96f else 1f, label = "hold")

    Box(
        modifier
            .scale(scale)
            .height(64.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Vigil.Raised)
            .border(1.dp, Vigil.LineStrong, RoundedCornerShape(percent = 50))
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { onConfirmed(); true }
            }
            .pointerInput(enabled, loading) {
                if (!enabled || loading) return@pointerInput
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            var lastTick = 0
                            progress.animateTo(1f, tween((durationMs * (1f - progress.value)).toInt(), easing = LinearEasing)) {
                                onProgress(value)
                                val tick = (value * 10).toInt()
                                if (tick > lastTick) {
                                    lastTick = tick
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onConfirmed()
                            progress.snapTo(0f)
                            onProgress(0f)
                        }
                        tryAwaitRelease()
                        if (job.isActive) {
                            job.cancel()
                            scope.launch {
                                progress.animateTo(0f, tween(260)) { onProgress(value) }
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Fill that sweeps left to right while holding.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(progress.value)
                .height(64.dp)
                .background(Vigil.FlameGradient),
        )
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Vigil.Flame, strokeWidth = 2.dp)
        } else {
            Text(
                label,
                style = Type.Button,
                color = if (progress.value > 0.5f) Vigil.Ink else Vigil.Bone,
            )
        }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .clip(shape)
            .background(Vigil.Surface)
            .border(1.dp, Vigil.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Vigil.Faint) {
    Text(text.uppercase(), style = Type.Overline, color = color, modifier = modifier)
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, color: Color = Vigil.Muted, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) StatusDot(color, Modifier.size(6.dp))
        Text(text, style = Type.Label, color = color)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Vigil.Flame.copy(alpha = 0.14f) else Vigil.Surface)
            .border(1.dp, if (selected) Vigil.Flame else Vigil.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text, style = Type.Headline.copy(fontSize = Type.Body.fontSize), color = if (selected) Vigil.Wick else Vigil.Bone)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, style = Type.Label, color = if (selected) Vigil.Flame else Vigil.Faint)
        }
    }
}

@Composable
fun HeirAvatar(name: String, index: Int, size: Dp = 40.dp) {
    val color = Vigil.HeirPalette[index % Vigil.HeirPalette.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "·" },
            style = Type.Label.copy(fontFamily = app.vigil.ui.theme.Sans),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/** Horizontal bar split by heir share, in heir colours. */
@Composable
fun SplitBar(shares: List<Int>, modifier: Modifier = Modifier) {
    val total = shares.sum().coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        shares.forEachIndexed { i, s ->
            if (s > 0) {
                Box(
                    Modifier
                        .weight(s / total.toFloat())
                        .height(10.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Vigil.HeirPalette[i % Vigil.HeirPalette.size]),
                )
            }
        }
        val remainder = 10_000 - shares.sum()
        if (remainder > 0 && total < 10_000) {
            Box(Modifier.weight(remainder / 10_000f).height(10.dp).clip(RoundedCornerShape(percent = 50)).background(Vigil.Line))
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false, valueColor: Color = Vigil.Bone) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = Type.Label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = if (mono) Type.Numeric.copy(fontSize = Type.Label.fontSize) else Type.Body, color = valueColor)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Vigil.Line))
}

@Composable
fun RowScope.Stat(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Vigil.Bone) {
    Column(modifier.weight(1f)) {
        Overline(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = Type.Title, color = accent)
    }
}
