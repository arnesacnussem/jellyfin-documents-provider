package a.sac.jellyfindocumentsprovider.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

@Entity
data class VirtualFile(
    @Id var id: Long = 0,
    @Index val documentId: String,
    val mimeType: String,
    val displayName: String,
    val lastModified: Long,
    val flags: Int,
    val size: Long,

    /**
     * Library ID
     */
    @Index val libId: String,

    /**
     * User ID
     */
    @Index val uid: String,

    /////////////////////// Media Info
    val duration: Long?,
    val year: Int?,
    val title: String?,
    val album: String?,
    val track: Int?,
    val artist: String?,
    val bitrate: Int?,
    val thumbnail: Boolean = false,
    val credentialId: Long
) {
    lateinit var credential: ToOne<Credential>
}