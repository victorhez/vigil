package app.vigil.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.vigil.R
import app.vigil.solana.VaultPhase

object Vigil {
    // Surfaces: warm near-black, like a room lit by one candle.
    val Ink = Color(0xFF0B0A09)
    val Surface = Color(0xFF151311)
    val Raised = Color(0xFF1D1A17)
    val Line = Color(0xFF2A2622)
    val LineStrong = Color(0xFF3A3530)

    // Text: bone, not white.
    val Bone = Color(0xFFF4EEE4)
    val Muted = Color(0xFFA39A8E)
    val Faint = Color(0xFF6B635A)

    // The flame.
    val Wick = Color(0xFFFFE3B0)
    val Flame = Color(0xFFFFA24C)
    val Ember = Color(0xFFFF6B35)
    val Blood = Color(0xFFE5383B)

    // After the flame goes out.
    val Ash = Color(0xFF8C97A8)
    val Moss = Color(0xFF9BC59D)

    val FlameGradient = Brush.linearGradient(listOf(Wick, Flame, Ember))

    fun phaseColor(phase: VaultPhase): Color = when (phase) {
        VaultPhase.Alive -> Flame
        VaultPhase.Overdue -> Ember
        VaultPhase.Expired -> Blood
        VaultPhase.Released -> Ash
    }

    /** Distinct but related hues for heirs in split bars and avatars. */
    val HeirPalette = listOf(Color(0xFFFFA24C), Color(0xFF9BC59D), Color(0xFF8FB3FF), Color(0xFFE7A1C8), Color(0xFFE8D27A))
}

private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    res,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(
    variable(R.font.inter_tight, 400),
    variable(R.font.inter_tight, 500),
    variable(R.font.inter_tight, 600),
    variable(R.font.inter_tight, 700),
)

val Mono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 700),
)

object Type {
    val Hero = TextStyle(fontFamily = Serif, fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-0.02).em, color = Vigil.Bone)
    val Display = TextStyle(fontFamily = Serif, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em, color = Vigil.Bone)
    val Title = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 32.sp, color = Vigil.Bone)
    val Headline = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, color = Vigil.Bone)
    val Body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = Vigil.Bone)
    val BodyMuted = Body.copy(color = Vigil.Muted)
    val Label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = Vigil.Muted)
    val Overline = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.14.em, color = Vigil.Faint)
    val Button = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.01.em)
    val Numeric = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Vigil.Bone)
    val Clock = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 44.sp, letterSpacing = (-0.03).em, color = Vigil.Bone)
}

@Composable
fun VigilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Vigil.Flame,
            onPrimary = Vigil.Ink,
            background = Vigil.Ink,
            onBackground = Vigil.Bone,
            surface = Vigil.Surface,
            onSurface = Vigil.Bone,
            surfaceVariant = Vigil.Raised,
            onSurfaceVariant = Vigil.Muted,
            outline = Vigil.Line,
            error = Vigil.Blood,
        ),
        content = content,
    )
}
