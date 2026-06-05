package arne.jellyfindocumentsprovider.vfs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class LyricsCache(
    @Id var id: Long = 0,
    @Index val vfDocId: String,
    val lyrics: String? = null,
)
