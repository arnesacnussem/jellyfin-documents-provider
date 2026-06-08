package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.ItemRecord

interface ItemRecordRepository {
    fun findByDocumentId(documentId: String): ItemRecord?
    fun put(vararg records: ItemRecord)
}
