package arne.jellyfindocumentsprovider.vfs

import android.database.MatrixCursor
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class VFSExtensionsTest {

    // ─── MatrixCursor.addRow ────────────────────────────────────

    @Test
    fun addRow_delegatesToNewRowAndAdd() {
        val cursor = mockk<MatrixCursor>()
        val builder = mockk<MatrixCursor.RowBuilder>(relaxed = true)
        every { cursor.newRow() } returns builder

        val result = cursor.addRow(mapOf("col1" to "val1", "col2" to 42))

        assertSame(builder, result)
        verify { cursor.newRow() }
        verify { builder.add("col1", "val1") }
        verify { builder.add("col2", 42) }
    }

    @Test
    fun addRow_emptyMap() {
        val cursor = mockk<MatrixCursor>()
        val builder = mockk<MatrixCursor.RowBuilder>(relaxed = true)
        every { cursor.newRow() } returns builder

        val result = cursor.addRow(emptyMap())

        assertSame(builder, result)
        verify(exactly = 1) { cursor.newRow() }
    }

    @Test
    fun addRow_multipleEntries_verifiesAll() {
        val cursor = mockk<MatrixCursor>()
        val builder = mockk<MatrixCursor.RowBuilder>(relaxed = true)
        every { cursor.newRow() } returns builder

        cursor.addRow(mapOf("a" to 1, "b" to 2, "c" to 3))

        verify { builder.add("a", 1) }
        verify { builder.add("b", 2) }
        verify { builder.add("c", 3) }
    }

    // ─── UUID.asString ─────────────────────────────────────────

    @Test
    fun uuidAsString_convertsToString() {
        val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", uuid.asString())
    }
}
