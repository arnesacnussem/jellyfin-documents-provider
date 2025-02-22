package arne.jellyfindocumentsprovider.vfs

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.workDataOf
import arne.jellyfindocumentsprovider.R
import arne.jellyfindocumentsprovider.hacks.fromMap
import arne.jellyfindocumentsprovider.ui.PROGRESS_NOTIFICATION_ID
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

class DatabaseSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "DatabaseSyncWorker"
    }

    val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notificationBuilder =
        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle("Syncing Database")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentText("starting...")
            .setSilent(true)

    @SuppressLint("RestrictedApi")
    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        val notification = notificationBuilder.build()
        notificationManager.notify(1, notification)
        return SettableFuture.create<ForegroundInfo?>().apply {
            set(ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification))
        }
    }

    override fun doWork(): Result {
        @Suppress("UNCHECKED_CAST")
        val request = fromMap<SyncRequest>(inputData.keyValueMap as Map<String, Any>)
        val idList = if (request.all) {
            ObjectBox.server.all
        } else {
            val fromDB = ObjectBox.server.get(request.id.toList())
            if (fromDB.size != request.id.size) {
                return Result.failure(
                    workDataOf(
                        "reason" to "Some of the server info not found"
                    )
                )
            }
            fromDB
        }

        return runBlocking {
            sync(idList)
        }
    }

    private suspend fun sync(credential: MutableList<JellyfinServer>): Result {
        if (credential.isEmpty()) {
            logcat(LogPriority.ERROR) {
                "some of the credential not found"
            }
            return Result.failure()
        }

        /**
         * -1 means pending
         * [0, 100) means progress
         * 100 means done
         */
        setProgressAsync(
            workDataOf(
                *credential.associate { it.uuid to -1 }.toList().toTypedArray()
            )
        )
        credential.forEachIndexed { index, c ->
            logcat {
                "syncing server: ${c.info}"
            }

            val task = DatabaseSyncTask(c.asAccessor(applicationContext))
            task.sync { text, proceed, total ->
                notificationManager.notify(
                    PROGRESS_NOTIFICATION_ID,
                    notificationBuilder
                        .setProgress(total, proceed, false)
                        .setContentTitle("[${index + 1}/${credential.size}] Syncing Database ${c.info}")
                        .setContentText(text)
                        .build()
                )
                val percent = if (total <= 0) -1 else 100 * proceed / total
                setProgressAsync(
                    workDataOf(c.uuid to percent)
                )
            }
        }

        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)

        return Result.success()
    }

    @Suppress("ArrayInDataClass")
    data class SyncRequest(
        val id: Array<Long> = emptyArray(),
        val all: Boolean = false
    )
}
