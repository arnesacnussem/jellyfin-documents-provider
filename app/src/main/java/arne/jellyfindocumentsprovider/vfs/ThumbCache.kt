package arne.jellyfindocumentsprovider.vfs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id


@Entity
data class ThumbCache(
    @Id var id: Long = 0, var data: ByteArray? = null, var checkedServer: Boolean = false
) {
    val notExists get() = data == null && checkedServer

    fun update(block: ThumbCache.() -> Unit) {
        block(this)
        ObjectBox.thumbCache.put(this)
    }
}