package a.sac.jellyfindocumentsprovider

import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.documentsprovider.RandomAccessBucket
import a.sac.jellyfindocumentsprovider.utils.PrefEnums.PrefKeys
import a.sac.jellyfindocumentsprovider.utils.getEnum
import android.app.Application
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger


@HiltAndroidApp
class JellyfinDocumentsProviderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBox.init(this)
        RandomAccessBucket.init(this)
        NotificationUtil.init(this)

        val logLevel = PreferenceManager.getDefaultSharedPreferences(this)
            .getEnum<LogPriority>(PrefKeys.LOG_LEVEL)
        LogcatLogger.install(AndroidLogcatLogger(logLevel))

    }
}