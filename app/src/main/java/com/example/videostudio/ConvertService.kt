package com.example.videostudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// কনভার্ট চলার সময় অ্যাপ মিনিমাইজ করলেও যেন বন্ধ না হয়
class ConvertService : Service() {

    companion object {
        private const val CH_RUN = "convert_running"
        private const val CH_DONE = "convert_done"
        private const val ID_RUN = 1
        private const val ID_DONE = 2

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ConvertService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    private val listener: () -> Unit = { refreshNotification() }
    @Volatile private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "Conversion progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Conversion finished", NotificationManager.IMPORTANCE_DEFAULT)
        )
        QueueManager.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildRunning("Starting...", 0, true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID_RUN, n)
        }
        refreshNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        QueueManager.removeListener(listener)
        super.onDestroy()
    }

    private fun openApp(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildRunning(text: String, p: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Video Studio")
            .setContentText(text)
            .setProgress(1000, p, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun refreshNotification() {
        if (finished) return
        val jobs = QueueManager.jobs.toList()
        val total = jobs.size
        val done = jobs.count {
            it.state != QueueManager.WAITING && it.state != QueueManager.RUNNING
        }
        if (total == 0 || done >= total) {
            complete(jobs)
            return
        }
        val sum = jobs.sumOf {
            when (it.state) {
                QueueManager.WAITING -> 0.0
                QueueManager.RUNNING -> it.progress.toDouble()
                else -> 1.0
            }
        }
        val frac = sum / total
        val text = "Running " + minOf(done + 1, total) + "/" + total + "  •  " + (frac * 100).toInt() + "%"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ID_RUN, buildRunning(text, (frac * 1000).toInt(), false))
    }

    private fun complete(jobs: List<Job>) {
        if (finished) return
        finished = true
        QueueManager.removeListener(listener)
        val ok = jobs.count { it.state == QueueManager.DONE }
        val bad = jobs.count { it.state == QueueManager.FAILED }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (jobs.isNotEmpty()) {
            val n = NotificationCompat.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Batch Process Complete")
                .setContentText("Successful: $ok    Failed: $bad")
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(ID_DONE, n)
        }
        stopSelf()
    }
}
