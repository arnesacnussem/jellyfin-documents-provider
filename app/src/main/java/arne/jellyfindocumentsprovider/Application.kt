package arne.jellyfindocumentsprovider

import android.app.Application
import arne.jellyfindocumentsprovider.common.InMemoryLogBuffer
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.provider.RandomAccessBucket
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
            LogcatLogger.install(InMemoryLogBuffer)
            InMemoryLogBuffer.setUiLogLevel(LogPriority.VERBOSE)
            AppDependencies.init(this)
            RandomAccessBucket.init(applicationContext.cacheDir.toPath())
        }
    }
}