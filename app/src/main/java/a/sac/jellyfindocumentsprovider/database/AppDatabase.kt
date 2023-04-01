package a.sac.jellyfindocumentsprovider.database

import a.sac.jellyfindocumentsprovider.database.dao.CredentialDao
import a.sac.jellyfindocumentsprovider.database.dao.VirtualFileDao
import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import android.content.Context
import androidx.room.*

@Database(entities = [Credential::class, VirtualFile::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun virtualFileDao(): VirtualFileDao

    companion object {
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE =
                        Room.databaseBuilder(context, AppDatabase::class.java, "database")
                            .build()
                }
            }
            return INSTANCE!!
        }
    }
}