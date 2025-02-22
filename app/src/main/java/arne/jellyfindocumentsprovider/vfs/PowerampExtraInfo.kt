package arne.jellyfindocumentsprovider.vfs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class PowerampExtraInfo(
    @Id var id: Long = 0,
    val lyrics: String?
) {
    fun getFakeWave() = FloatArray(100) { -1f + 2f * Math.random().toFloat() }
}