package arne.jellyfindocumentsprovider.vfs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient


@Entity
data class ThumbCache(
    @Id var id: Long = 0, var data: ByteArray? = null, var checkedServer: Boolean = false
) {
    val notExists get() = data == null && checkedServer

    @Transient
    var persistCallback: ((ThumbCache) -> Unit)? = null

    fun update(block: ThumbCache.() -> Unit) {
        block(this)
        if (persistCallback != null) {
            persistCallback!!(this)
        } else {
            ObjectBox.thumbCache.put(this)
        }
    }
}