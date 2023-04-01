package a.sac.jellyfindocumentsprovider.database.dao

import a.sac.jellyfindocumentsprovider.database.entities.Credential
import androidx.room.*

@Dao
interface CredentialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(credential: Credential)

    @Delete
    fun delete(credential: Credential)

    @Query("SELECT * FROM credential")
    fun loadAllUsers(): Array<Credential>

    @Query("SELECT * FROM credential WHERE uid = :uid")
    fun getByUid(uid: String): Credential

    @Query("UPDATE credential SET library = :lib WHERE uid = :uid")
    fun updateLibrary(uid: String, lib: Map<String, String>)
}