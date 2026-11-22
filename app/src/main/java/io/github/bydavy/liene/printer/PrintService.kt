package io.github.bydavy.liene.printer

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.bydavy.liene.LieneApplication
import io.github.bydavy.liene.MainActivity
import io.github.bydavy.liene.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class PrintService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: PrinterRepository
    private lateinit var notifications: NotificationManager
    private var progressJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as LieneApplication).repository
        notifications = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            repository.cancelJob()
            return START_NOT_STICKY
        }

        val path = intent?.getStringExtra(EXTRA_FILE)
        val copies = intent?.getIntExtra(EXTRA_COPIES, 1) ?: 1
        if (path.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification(notification("Preparing print…", indeterminate = true))
        acquireWakeLock()

        val file = File(path)
        progressJob = scope.launch {
            repository.state.collectLatest { state ->
                val text = when {
                    state.uploadTotal > 0 && state.uploadSent < state.uploadTotal ->
                        "Uploading ${state.uploadSent}/${state.uploadTotal} frames"
                    state.job?.state == 3 ->
                        "Printing page ${state.job.page.coerceAtLeast(1)}"
                    state.job != null ->
                        "Waiting for the printer (${jobStateLabel(state.job.state)})"
                    else -> "Preparing print…"
                }
                val progress = if (state.uploadTotal > 0) {
                    100 * state.uploadSent / state.uploadTotal
                } else {
                    null
                }
                notifications.notify(
                    NOTIFICATION_ID,
                    notification(text, progress = progress, indeterminate = progress == null),
                )
            }
        }

        scope.launch {
            val success = repository.print(file, copies)
            progressJob?.cancel()
            releaseWakeLock()
            runCatching { file.delete() }

            val finalState = repository.state.value
            val finalText = when {
                success && finalState.job?.state == 9 -> "Print finished"
                success && finalState.job?.state == 8 -> "Print canceled"
                success && finalState.job?.state == 7 -> "Print aborted"
                success -> "Print finished"
                else -> finalState.error ?: "Print failed"
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifications.notify(
                NOTIFICATION_ID,
                notification(finalText, ongoing = false),
            )
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        progressJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(
        text: String,
        progress: Int? = null,
        indeterminate: Boolean = false,
        ongoing: Boolean = true,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, PrintService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Liene")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (ongoing) {
            builder.addAction(0, "Cancel", cancel)
        }
        when {
            indeterminate -> builder.setProgress(0, 0, true)
            progress != null -> builder.setProgress(100, progress.coerceIn(0, 100), false)
            else -> {}
        }
        return builder.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Printing",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Photo upload and print progress"
            setShowBadge(false)
        }
        notifications.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Liene:print").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "printing"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_CANCEL = "io.github.bydavy.liene.CANCEL_PRINT"
        private const val EXTRA_FILE = "io.github.bydavy.liene.PRINT_FILE"
        private const val EXTRA_COPIES = "io.github.bydavy.liene.PRINT_COPIES"

        fun start(context: Context, file: File, copies: Int) {
            val intent = Intent(context, PrintService::class.java).apply {
                putExtra(EXTRA_FILE, file.absolutePath)
                putExtra(EXTRA_COPIES, copies)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
