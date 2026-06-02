package arne.jellyfindocumentsprovider

import android.app.Application
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.provider.RandomAccessBucket
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger


@Volatile
var isInitialized = false

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeIfNeeded()
    }

    fun initializeIfNeeded() {
        if (!isInitialized) {
            isInitialized = true
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
            AppDependencies.init(this)
            RandomAccessBucket.init(applicationContext.cacheDir.toPath())
        }
    }
}