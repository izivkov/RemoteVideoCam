package org.avmedia.remotevideocam.frameanalysis.motion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.avmedia.remotevideocam.R
import timber.log.Timber
import java.util.Date
import java.util.Locale

private const val CHANNEL_MOTION_DETECTED = "motion_detected"
private const val SUMMARY_ID = 0

private val VIBRATION_PATTERN = longArrayOf(0, 250, 250, 250)
private const val COOLDOWN_MS = 30_000L // 30-second cooldown window before the next notification
private const val MOTION_DETECTION_NOTIFY_GROUP = "motion_detection_notify_group"

private const val TAG = "MotionNotificationController"

class MotionNotificationController(private val context: Context) {

    private val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val notificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    private var showNotificationMs: Long = -1
    private var notificationId = 1


    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Motion Detection"
            val descriptionText = "Monitor any movement in camera frames."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(CHANNEL_MOTION_DETECTED, name, importance).apply {
                description = descriptionText
                vibrationPattern = VIBRATION_PATTERN
                setSound(notificationUri, audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(title: String) {
        if (skipOrRecordNotification()) {
            return
        }

        val simpleDataFormat = SimpleDateFormat(
            "hh:mm:ss",
            Locale.getDefault())
            .format(Date())

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showToast(title.plus(" at $simpleDataFormat"))
            return
        }
        val notification = createNotificationBuilder(title)
            .setContentText("At $simpleDataFormat")
            .build()
        val summary = createNotificationBuilder(title)
            .setGroupSummary(true)
            .build()

        notificationManagerCompat.notify(notificationId++, notification)
        notificationManagerCompat.notify(SUMMARY_ID, summary)
    }

    private fun createNotificationBuilder(title: String) =
        NotificationCompat.Builder(context, CHANNEL_MOTION_DETECTED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_motion_detection)
            .setContentTitle(title)
            .setVibrate(VIBRATION_PATTERN)
            .setSound(notificationUri)
            .setGroup(MOTION_DETECTION_NOTIFY_GROUP)

    private fun skipOrRecordNotification(): Boolean {
        val diff = SystemClock.elapsedRealtime() - showNotificationMs
        return if (diff < COOLDOWN_MS) {
            Timber.tag(TAG).d(
                "Skip notification due to cooldown in %s seconds",
                (COOLDOWN_MS - diff) / 1000
            )
            true
        } else {
            showNotificationMs = SystemClock.elapsedRealtime()
            false
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
