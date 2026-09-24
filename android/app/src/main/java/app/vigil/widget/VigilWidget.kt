package app.vigil.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.vigil.MainActivity
import app.vigil.VigilApplication
import app.vigil.data.VaultSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class VigilWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = (context.applicationContext as VigilApplication).container.prefs.snapshot
        provideContent { Content(context, snapshot) }
    }

    @Composable
    private fun Content(context: Context, snapshot: VaultSnapshot?) {
        val now = System.currentTimeMillis() / 1000
        val open = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CHECK_IN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val (headline, detail, fraction, tint) = when {
            snapshot == null -> Quad("Unlit", "Open Vigil to begin", 0f, ASH)
            snapshot.releasedAt != 0L -> Quad("Released", "Passed to your heirs", 0f, ASH)
            now <= snapshot.dueAt -> Quad(
                "by ${WHEN.format(Instant.ofEpochSecond(snapshot.dueAt))}",
                "Day ${snapshot.streak} streak",
                ((snapshot.dueAt - now).toFloat() / snapshot.interval).coerceIn(0f, 1f),
                FLAME,
            )
            now <= snapshot.deadline -> Quad("Overdue", "Grace ends ${WHEN.format(Instant.ofEpochSecond(snapshot.deadline))}", 0.04f, EMBER)
            else -> Quad("Expired", "Revive with your wallet", 0f, BLOOD)
        }

        Row(
            GlanceModifier.fillMaxSize().background(INK).cornerRadius(28.dp).padding(16.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(dial(fraction, tint)), contentDescription = null, modifier = GlanceModifier.size(72.dp))
            Spacer(GlanceModifier.width(14.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text("VIGIL · NEXT CHECK-IN", style = TextStyle(color = ColorProvider(FAINT), fontSize = 10.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                Text(headline, style = TextStyle(color = ColorProvider(BONE), fontSize = 20.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(detail, style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp), maxLines = 1)
                Spacer(GlanceModifier.height(10.dp))
                if (snapshot != null && snapshot.releasedAt == 0L) {
                    Box(
                        GlanceModifier.fillMaxWidth().height(36.dp).background(tint).cornerRadius(18.dp).clickable(open),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Clock in", style = TextStyle(color = ColorProvider(INK), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }

    private data class Quad(val a: String, val b: String, val c: Float, val d: Color)

    /** Rasterised mini dial: ticks lit by time remaining around a small flame. */
    private fun dial(fraction: Float, tint: Color): Bitmap {
        val size = 216
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val c = size / 2f
        val argb = android.graphics.Color.argb((tint.alpha * 255).toInt(), (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt())
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(c, c, c * 0.62f, intArrayOf(0xFFFFE3B0.toInt(), argb, 0x00000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            alpha = if (fraction > 0f) 255 else 90
        }
        canvas.drawCircle(c, c, c * 0.62f, glow)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        val count = 48
        val lit = (fraction * count).toInt()
        for (i in 0 until count) {
            val a = Math.toRadians(i * 360.0 / count - 90)
            tick.color = if (i < lit) argb else 0xFF3A3530.toInt()
            val inner = c * if (i % 4 == 0) 0.8f else 0.86f
            canvas.drawLine(
                c + (Math.cos(a) * inner).toFloat(), c + (Math.sin(a) * inner).toFloat(),
                c + (Math.cos(a) * c * 0.97f).toFloat(), c + (Math.sin(a) * c * 0.97f).toFloat(),
                tick,
            )
        }
        return bmp
    }

    companion object {
        private val INK = Color(0xFF151311)
        private val BONE = Color(0xFFF4EEE4)
        private val MUTED = Color(0xFFA39A8E)
        private val FAINT = Color(0xFF6B635A)
        private val FLAME = Color(0xFFFFA24C)
        private val EMBER = Color(0xFFFF6B35)
        private val BLOOD = Color(0xFFE5383B)
        private val ASH = Color(0xFF8C97A8)
        private val WHEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US).withZone(ZoneId.systemDefault())

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { VigilWidget().updateAll(app) } }
        }
    }
}

class VigilWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VigilWidget()
}
