package hu.galvill.frekvencia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/** Előtér-szolgáltatás: képernyőzárás és háttér mellett is szól */
class PlayService : Service() {

    companion object {
        const val ACTION_STOP = "hu.galvill.frekvencia.STOP"
        const val ACTION_UPDATE = "hu.galvill.frekvencia.UPDATE"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (Engine.running) Engine.requestStop() else shutdown()
            }
            ACTION_UPDATE -> {
                if (Engine.running) {
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
                }
            }
            else -> {
                createChannel()
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIF_ID, buildNotification())
                }
                if (wakeLock == null) {
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Frekvencia:play").apply {
                        setReferenceCounted(false)
                        acquire(3 * 60 * 60 * 1000L)
                    }
                }
                Engine.onFinished = { main.post { if (!Engine.running) shutdown() } }
                Engine.start()
            }
        }
        return START_NOT_STICKY
    }

    private fun shutdown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Lejátszás", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val modeText = when (Engine.mode) {
            Mode.BINAURAL -> "Binaurális · vivő ${fmt(Engine.carrier)} Hz"
            Mode.ISO -> "Izokronikus · vivő ${fmt(Engine.carrier)} Hz"
            Mode.PURE -> "Tiszta hang"
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${fmt(Engine.freq)} Hz · ${Engine.name}")
            .setContentText(modeText)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notif), "Leállítás", stop
                ).build()
            )
            .build()
    }
}
