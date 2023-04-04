package a.sac.jellyfindocumentsprovider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationUtil {

    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    fun init(context: Context) {
        appContext = context.applicationContext
        notificationManager = context.getSystemService(NotificationManager::class.java)


        CHANNEL_ID_MAP.forEach { t, u -> createNotificationChannel(t, u.first, u.second) }
    }


    private val CHANNEL_ID_MAP = mapOf(
        "Download" to ("Show download progress." to NotificationManager.IMPORTANCE_LOW)
    )

    private fun createNotificationChannel(name: String, desc: String, importance: Int) {
        val channel = NotificationChannel(name, name, importance).apply {
            description = desc
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showOrUpdateNotification(
        id: Int,
        notification: (context: Context) -> Notification
    ) {
        notificationManager.notify(id, notification(appContext))
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}