package arne.jellyfindocumentsprovider.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import arne.jellyfindocumentsprovider.vfs.DatabaseSyncWorker.Companion.NOTIFICATION_CHANNEL_ID


fun NotificationManager.globalCreateChannels() {
    createNotificationChannel(
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Sync Database",
            NotificationManager.IMPORTANCE_HIGH
        )
    )
}

const val PROGRESS_NOTIFICATION_ID = 1