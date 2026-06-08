package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.ItemRecord
import arne.jellyfindocumentsprovider.vfs.ItemRecord_
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

class ObjectBoxItemRecordRepository(
    private val box: Box<ItemRecord>
) : ItemRecordRepository {
    override fun findByDocumentId(documentId: String) = box.query {
        equal(ItemRecord_.documentId, documentId, QueryBuilder.StringOrder.CASE_SENSITIVE)
    }.findFirst()

    override fun put(vararg records: ItemRecord) {
        box.put(records.toList())
    }
}
