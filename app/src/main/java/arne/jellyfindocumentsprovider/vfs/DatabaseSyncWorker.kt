package arne.jellyfindocumentsprovider.vfs

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.workDataOf
import arne.jellyfindocumentsprovider.R
import arne.jellyfindocumentsprovider.common.PowerampScanToggle
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.getEnum
import arne.jellyfindocumentsprovider.hacks.fromMap
import arne.jellyfindocumentsprovider.hacks.toPropertyMap
import arne.jellyfindocumentsprovider.ui.PROGRESS_NOTIFICATION_ID
import com.google.common.util.concurrent.ListenableFuture
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
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
        val favoritesOnly = inputData.getBoolean("favorites_only", false)
        return runBlocking {
            sync(servers = arne.jellyfindocumentsprovider.data.AppDependencies.repos.server.findAll(), favoritesOnly)
        }
    }

    private suspend fun sync(servers: List<JellyfinServer>, favoritesOnly: Boolean = false): Result {
        if (servers.isEmpty()) {
            logcat(LogPriority.WARN) {
                "some of the credential not found"
            }
            return Result.failure()
        }

        setProgressAsync(
            SyncTaskProgress(-1, servers.size).toWorkData()
        )
        servers.forEachIndexed { index, c ->
            logcat {
                "syncing server: ${c.info}"
            }

            val task = DatabaseSyncTask(
                api = c.asAccessor(applicationContext),
                repos = arne.jellyfindocumentsprovider.data.AppDependencies.repos,
                credential = c,
                context = applicationContext,
            )
            val title = if (favoritesOnly) "Favorites" else "Syncing Database"
            if (favoritesOnly) {
                task.syncFavorites { text, proceed, total ->
                    notificationManager.notify(
                        PROGRESS_NOTIFICATION_ID,
                        notificationBuilder
                            .setProgress(total, proceed, false)
                            .setContentTitle("[${index + 1}/${servers.size}] $title ${c.info}")
                            .setContentText(text)
                            .build()
                    )
                    val percent = if (total <= 0) -1 else 100 * proceed / total
                    setProgressAsync(
                        SyncTaskProgress(proceed, total).toWorkData()
                    )
                }
            } else {
                task.sync { text, proceed, total ->
                    notificationManager.notify(
                        PROGRESS_NOTIFICATION_ID,
                        notificationBuilder
                            .setProgress(total, proceed, false)
                            .setContentTitle("[${index + 1}/${servers.size}] $title ${c.info}")
                            .setContentText(text)
                            .build()
                    )
                    val percent = if (total <= 0) -1 else 100 * proceed / total
                    setProgressAsync(
                        SyncTaskProgress(proceed, total).toWorkData()
                    )
                }
            }
        }

        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.getEnum<PowerampScanToggle>(PrefKeys.POWERAMP_SCAN_ON_SYNC) == PowerampScanToggle.ENABLED) {
            notifyPowerampScan(applicationContext)
        }

        return Result.success()
    }

    private fun notifyPowerampScan(context: Context) {
        try {
            val cn = PowerampAPIHelper.getScannerServiceComponentName(context)
            val intent = Intent(PowerampAPI.Scanner.ACTION_SCAN_DIRS).apply {
                component = cn
                putExtra(PowerampAPI.Scanner.EXTRA_SCAN_PROVIDERS, true)
                putExtra(PowerampAPI.Scanner.EXTRA_PROVIDER, "arne.jellyfindocumentsprovider")
                putExtra(PowerampAPI.Scanner.EXTRA_CAUSE, "sync_complete")
            }
            context.startService(intent)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "notifyPowerampScan: ${e.message}" }
        }
    }

    @Suppress("ArrayInDataClass")
    data class SyncTaskProgress(
        val current: Int,
        val total: Int,
        val step: Int = 0,
        val totalSteps: Int = 0,
    ) {
        fun toWorkData() = workDataOf(*this.toPropertyMap().toList().toTypedArray())

        companion object {
            fun fromWorkData(data: Map<String, Any>) = fromMap<SyncTaskProgress>(data)
        }
    }
}
