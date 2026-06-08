package arne.jellyfindocumentsprovider.vfs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

@Entity
data class AlbumInfo(
    @Id var id: Long = 0,
    @Index val uuid: String = "",
    @Index val libId: String = "",
    val name: String = "",
    @Index val serverId: Long = 0,
) {
    lateinit var thumbCache: ToOne<ThumbCache>
}