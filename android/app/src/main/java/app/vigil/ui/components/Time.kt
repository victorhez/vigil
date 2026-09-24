package app.vigil.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Unix seconds, ticking once a second while composed. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis() / 1000
        }
    }
    return now
}

object TimeText {
    const val MINUTE = 60L
    const val HOUR = 3_600L
    const val DAY = 86_400L

    /** "04:12:09" under a day, "3d 04h" beyond. */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s < DAY) {
            "%02d:%02d:%02d".format(Locale.US, s / HOUR, (s % HOUR) / MINUTE, s % MINUTE)
        } else {
            "%dd %02dh".format(Locale.US, s / DAY, (s % DAY) / HOUR)
        }
    }

    /** Human cadence: "every day", "every 3 days", "every 5 minutes". */
    fun cadence(seconds: Long): String = "every " + span(seconds, singularArticle = false)

    fun span(seconds: Long, singularArticle: Boolean = true): String {
        fun unit(n: Long, name: String) = if (n == 1L) (if (singularArticle) "1 $name" else name) else "$n ${name}s"
        return when {
            seconds <= 0 -> "no time"
            seconds % (30 * DAY) == 0L && seconds >= 30 * DAY -> unit(seconds / (30 * DAY), "month")
            seconds % (7 * DAY) == 0L && seconds >= 7 * DAY -> unit(seconds / (7 * DAY), "week")
            seconds % DAY == 0L -> unit(seconds / DAY, "day")
            seconds % HOUR == 0L -> unit(seconds / HOUR, "hour")
            seconds % MINUTE == 0L -> unit(seconds / MINUTE, "minute")
            else -> unit(seconds, "second")
        }
    }

    fun relative(unix: Long, now: Long): String {
        val d = now - unix
        return when {
            d < 45 -> "just now"
            d < HOUR -> "${d / MINUTE}m ago"
            d < DAY -> "${d / HOUR}h ago"
            d < 7 * DAY -> "${d / DAY}d ago"
            else -> date(unix)
        }
    }

    fun date(unix: Long): String =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(unix))

    fun dateTime(unix: Long): String =
        DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.US).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(unix))
}
