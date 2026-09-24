package app.vigil.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vigil.MainActivity
import app.vigil.R
import app.vigil.VigilApplication
import app.vigil.data.VaultSnapshot
import app.vigil.solana.PublicKey
import app.vigil.ui.components.TimeText
import app.vigil.widget.VigilWidget
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK = "vigil.reminders"
    const val CHANNEL = "checkins"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Check-in reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Nudges before your check-in window closes"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun sync(context: Context) {
        val app = context.applicationContext as VigilApplication
        val wm = WorkManager.getInstance(context)
        if (!app.container.prefs.remindersEnabled || app.container.prefs.snapshot == null) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/**
 * Re-reads the vault from the chain, refreshes the widget, and sends at most one notification per stage:
 * 1. a quarter of the window left, 2. window missed (grace running), 3. vault expired.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VigilApplication).container
        val prefs = container.prefs
        val cached = prefs.snapshot ?: return Result.success()

        val fresh = runCatching { container.repository.vaultAt(PublicKey.of(cached.vault)) }.getOrNull()
        val snapshot = fresh?.let(VaultSnapshot::of) ?: cached
        prefs.snapshot = snapshot
        VigilWidget.refresh(applicationContext)

        if (!prefs.remindersEnabled || snapshot.releasedAt != 0L) return Result.success()

        val now = System.currentTimeMillis() / 1000
        val left = snapshot.dueAt - now
        val stage = when {
            now > snapshot.deadline -> 3
            now > snapshot.dueAt -> 2
            left < snapshot.interval / 4 -> 1
            else -> 0
        }
        if (stage > prefs.reminderStage(snapshot.deadline)) {
            prefs.setReminderStage(snapshot.deadline, stage)
            val (title, body) = when (stage) {
                1 -> "Time to clock in" to "${TimeText.clock(left)} left in this window. Hold and touch the sensor."
                2 -> "You missed a check-in" to "Grace period running. Release opens in ${TimeText.clock(snapshot.deadline - now)}."
                else -> "Your Vigil has expired" to "Open Vigil and revive it with your wallet before your heirs release it."
            }
            notify(title, body)
        }
        return Result.success()
    }

    private fun notify(title: String, body: String) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_CHECK_IN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vigil)
            .setColor(0xFFFFA24C.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(0, "Clock in", open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(7, notification)
    }
}
