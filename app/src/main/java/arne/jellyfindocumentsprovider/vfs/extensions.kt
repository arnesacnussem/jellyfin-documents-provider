package arne.jellyfindocumentsprovider.vfs

import android.database.MatrixCursor
import org.jellyfin.sdk.model.UUID

fun MatrixCursor.addRow(row: Map<String, Any>): MatrixCursor.RowBuilder? {
    val newRow = newRow()
    row.forEach { (key, value) -> newRow.add(key, value) }
    return newRow
}


fun UUID.asString() = toString()