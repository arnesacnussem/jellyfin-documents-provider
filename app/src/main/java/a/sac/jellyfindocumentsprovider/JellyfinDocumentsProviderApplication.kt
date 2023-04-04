package a.sac.jellyfindocumentsprovider

import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.documentsprovider.RandomAccessBucket
import android.app.Application
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class JellyfinDocumentsProviderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBox.init(this)
        RandomAccessBucket.init(this.applicationContext.cacheDir.toPath())
        NotificationUtil.init(this)
    }
}