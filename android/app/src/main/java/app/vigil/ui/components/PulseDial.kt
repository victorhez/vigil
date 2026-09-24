package app.vigil.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.vigil.solana.VaultPhase
import app.vigil.ui.theme.Vigil
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The heart of the app: a clock face whose lit ticks are the time you have left,
 * wrapped around a flame that beats while you are alive and dims as the deadline nears.
 */
@Composable
fun PulseDial(
    remaining: Float,
    phase: VaultPhase,
    hold: Float,
    burstKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val color by animateColorAsState(Vigil.phaseColor(phase), tween(800), label = "phase")
    val lit by animateFloatAsState(remaining.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "lit")
    val alive = phase == VaultPhase.Alive || phase == VaultPhase.Overdue

    val beat = rememberInfiniteTransition(label = "beat")
    val pulse by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = if (phase == VaultPhase.Overdue) 700 else 1150
                1f at 0
                1.075f at 110 using FastOutSlowInEasing
                0.995f at 240
                1.04f at 340 using FastOutSlowInEasing
                1f at 560
            },
        ),
        label = "pulse",
    )
    val flicker by beat.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "flicker",
    )

    val burst = remember { Animatable(1f) }
    LaunchedEffect(burstKey) {
        if (burstKey != null) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
    }
    val sparks = remember(burstKey) {
        val rnd = Random(burstKey?.hashCode() ?: 7)
        List(36) { Spark(rnd.nextFloat() * 2f * PI.toFloat(), 0.55f + rnd.nextFloat() * 0.6f, 1.5f + rnd.nextFloat() * 3f) }
    }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center

            drawFlame(c, r * 0.64f, color, if (alive) pulse else 1f, if (alive) 0.35f + 0.65f * lit else 0.18f, flicker)
            drawTicks(c, r, lit, color)
            drawHold(c, r, hold)
            if (burst.value < 1f) drawSparks(c, r, burst.value, sparks)
        }
        content()
    }
}

private data class Spark(val angle: Float, val reach: Float, val size: Float)

private fun DrawScope.drawFlame(c: Offset, radius: Float, color: Color, scale: Float, intensity: Float, flicker: Float) {
    val wobble = 1f + 0.015f * sin(flicker * 2f * PI.toFloat() * 3f)
    val r = radius * scale * wobble
    // Outer halo
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.28f * intensity),
            0.55f to color.copy(alpha = 0.08f * intensity),
            1f to Color.Transparent,
            center = c,
            radius = r * 1.55f,
        ),
        radius = r * 1.55f,
        center = c,
    )
    // Body
    drawCircle(
        brush = Brush.radialGradient(
            0f to Vigil.Wick.copy(alpha = 0.95f * intensity),
            0.35f to color.copy(alpha = 0.85f * intensity),
            0.75f to Vigil.Ember.copy(alpha = 0.35f * intensity),
            1f to Color.Transparent,
            center = Offset(c.x, c.y + r * 0.08f),
            radius = r,
        ),
        radius = r,
        center = c,
    )
    // Hot core
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.55f * intensity),
            1f to Color.Transparent,
            center = Offset(c.x, c.y + r * 0.12f),
            radius = r * 0.38f,
        ),
        radius = r * 0.38f,
        center = Offset(c.x, c.y + r * 0.12f),
    )
}

private fun DrawScope.drawTicks(c: Offset, r: Float, lit: Float, color: Color) {
    val count = 60
    val litCount = (lit * count).toInt()
    for (i in 0 until count) {
        val major = i % 5 == 0
        val angle = (i / count.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val outer = r * 0.985f
        val inner = r * if (major) 0.9f else 0.93f
        val on = i < litCount
        val tint = when {
            on -> color.copy(alpha = if (major) 1f else 0.8f)
            else -> Vigil.LineStrong.copy(alpha = if (major) 0.9f else 0.55f)
        }
        drawLine(
            color = tint,
            start = Offset(c.x + cos(angle) * inner, c.y + sin(angle) * inner),
            end = Offset(c.x + cos(angle) * outer, c.y + sin(angle) * outer),
            strokeWidth = if (major) 2.6.dp.toPx() else 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawHold(c: Offset, r: Float, hold: Float) {
    if (hold <= 0f) return
    val ringR = r * 0.86f
    rotate(-90f, c) {
        drawArc(
            color = Vigil.Bone.copy(alpha = 0.9f),
            startAngle = 0f,
            sweepAngle = 360f * hold,
            useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSparks(c: Offset, r: Float, t: Float, sparks: List<Spark>) {
    val fade = 1f - t
    drawCircle(Vigil.Wick.copy(alpha = 0.35f * fade), radius = r * (0.6f + 0.5f * t), center = c, style = Stroke(2.dp.toPx() * fade + 0.5f))
    sparks.forEach { s ->
        val d = r * (0.3f + s.reach * t)
        val p = Offset(c.x + cos(s.angle) * d, c.y + sin(s.angle) * d)
        drawCircle(Vigil.Wick.copy(alpha = fade), radius = s.size.dp.toPx() * fade, center = p)
    }
}
