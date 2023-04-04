package a.sac.jellyfindocumentsprovider

import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.documentsprovider.RandomAccessBucket
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import logcat.AndroidLogcatLogger
import logcat.LogPriority.DEBUG
import logcat.LogcatLogger


@HiltAndroidApp
class JellyfinDocumentsProviderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogcatLogger.install(AndroidLogcatLogger(DEBUG))
        ObjectBox.init(this)
        RandomAccessBucket.init(this.applicationContext.cacheDir.toPath())
        NotificationUtil.init(this)

    }
}