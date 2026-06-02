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
    createNotificationChannel(
        NotificationChannel(
            METADATA_CHANNEL_ID,
            "Metadata Fetch",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Thumbnail and metadata downloads"
            setSound(null, null)
        }
    )
    createNotificationChannel(
        NotificationChannel(
            NETWORK_CHANNEL_ID,
            "Network Transfer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Audio file streaming and downloads"
            setSound(null, null)
        }
    )
}

const val PROGRESS_NOTIFICATION_ID = 1
const val METADATA_CHANNEL_ID = "MetadataFetch"
const val NETWORK_CHANNEL_ID = "NetworkTransfer"
const val METADATA_NOTIFICATION_ID = 2
const val NETWORK_NOTIFICATION_ID = 3