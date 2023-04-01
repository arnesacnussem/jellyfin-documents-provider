package a.sac.jellyfindocumentsprovider.database.dao

import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VirtualFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(virtualFile: List<VirtualFile>?)

    @Query("DELETE FROM vFile WHERE uid = :uid")
    fun deleteAllByUid(uid: String)

    @Query("DELETE FROM vFile WHERE lid = :lid")
    fun deleteAllByLibraryId(lid: String)

    @Query("SELECT * FROM vFile WHERE lid = :lid")
    fun getAllByLibraryId(lid: String): List<VirtualFile>

    @Query("SELECT * FROM vFile")
    fun getAll(): List<VirtualFile>

    @Query("SELECT * FROM vFile WHERE id = :id")
    fun getById(id: String): VirtualFile
}