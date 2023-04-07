package a.sac.jellyfindocumentsprovider.database.entities

import a.sac.jellyfindocumentsprovider.SortedLongRangeList
import a.sac.jellyfindocumentsprovider.database.ListLongRangeConvert
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
    val isCompleted: Boolean,
    @Convert(converter = ListLongRangeConvert::class, dbType = String::class)
    val bufferedRanges: SortedLongRangeList
) {
    lateinit var virtualFile: ToOne<VirtualFile>
}