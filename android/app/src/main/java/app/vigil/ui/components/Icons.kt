package app.vigil.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn for Vigil on a 24px grid, 1.8px stroke. */
object Icons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Pulse = icon("pulse", "M2 12h4l2.5-6 4 13 3-9 2 2h4.5")
    val Vault = icon(
        "vault",
        "M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5z",
        "M12 9.2a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6z",
        "M12 9.2V7.5M12 16.5v-1.7M9.2 12H7.5M16.5 12h-1.7",
    )
    val Heirs = icon(
        "heirs",
        "M9 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 1 0 0 7z",
        "M2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5",
        "M16 4.3a3.3 3.3 0 0 1 0 6.4M18 14.8c1.9.7 3.2 2.5 3.5 5.2",
    )
    val Legacy = icon(
        "legacy",
        "M12 3c2.5 3 4.5 5.4 4.5 8.3A4.5 4.5 0 0 1 12 16a4.5 4.5 0 0 1-4.5-4.7C7.5 8.4 9.5 6 12 3z",
        "M5 21h14M8 18.5h8",
    )
    val Settings = icon(
        "settings",
        "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
        "M19.4 13.5l1.6 1.2-2 3.4-1.9-.7a7.6 7.6 0 0 1-2 1.2L14.8 21h-4l-.3-2.4a7.6 7.6 0 0 1-2-1.2l-1.9.7-2-3.4 1.6-1.2a7.7 7.7 0 0 1 0-2.9L4.6 9.4l2-3.4 1.9.7a7.6 7.6 0 0 1 2-1.2L10.8 3h4l.3 2.5a7.6 7.6 0 0 1 2 1.2l1.9-.7 2 3.4-1.6 1.2a7.7 7.7 0 0 1 0 2.9z",
    )
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Minus = icon("minus", "M5 12h14")
    val Arrow = icon("arrow", "M5 12h14M13 6l6 6-6 6")
    val ArrowUp = icon("arrow_up", "M12 19V5M6 11l6-6 6 6")
    val ArrowDown = icon("arrow_down", "M12 5v14M6 13l6 6 6-6")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Copy = icon("copy", "M9 9h10v10H9z", "M5 15V5h10")
    val Scan = icon(
        "scan",
        "M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16",
        "M4 12h16",
    )
    val External = icon("external", "M14 4h6v6M20 4l-9 9", "M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10")
    val Fingerprint = icon(
        "fingerprint",
        "M7 18.5c1-1.8 1.5-3.8 1.5-6.5a3.5 3.5 0 0 1 7 0c0 1.4-.1 2.7-.4 4",
        "M12 12c0 3.5-.8 6.3-2.3 8.5",
        "M4.5 15.5c.3-1.1.5-2.3.5-3.5a7 7 0 0 1 13.4-2.8",
        "M18.9 12.5c0 2.3-.4 4.5-1.2 6.5",
    )
    val Refresh = icon("refresh", "M20 11a8 8 0 0 0-14.6-4.5L4 8M4 4v4h4", "M4 13a8 8 0 0 0 14.6 4.5L20 16M20 20v-4h-4")
    val Shield = icon("shield", "M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z", "M8.8 12l2.2 2.2 4.2-4.4")
    val Clock = icon("clock", "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z", "M12 7.5V12l3 2")
    val Drop = icon("drop", "M12 3.5c3 3.6 5.5 6.8 5.5 10a5.5 5.5 0 0 1-11 0c0-3.2 2.5-6.4 5.5-10z")
}
