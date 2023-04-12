package a.sac.jellyfindocumentsprovider.database.entities

import a.sac.jellyfindocumentsprovider.database.SortedLongRangeListConvert
import a.sac.jellyfindocumentsprovider.utils.SortedLongRangeList
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

@Entity
data class CacheInfo(
    @Id var id: Long = 0,
    @Index val virtualFileId: Long,
    @Index val vfDocId: String,
    val localPath: String,
    val localLength: Long = 0,
    val bitrate: Int = -1,
    val isCompleted: Boolean = false,
    @Convert(converter = SortedLongRangeListConvert::class, dbType = String::class)
    val bufferedRanges: SortedLongRangeList
) {
    lateinit var virtualFile: ToOne<VirtualFile>
    val isComplete
        get() = isCompleted or bufferedRanges.noGapsIn(0 until virtualFile.target.size)
}
