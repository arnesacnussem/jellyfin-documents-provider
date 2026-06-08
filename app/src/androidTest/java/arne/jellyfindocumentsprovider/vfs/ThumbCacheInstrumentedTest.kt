package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ThumbCacheInstrumentedTest {

    private lateinit var store: BoxStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = MyObjectBox.builder()
            .androidContext(context)
            .name("thumb-test-${java.util.UUID.randomUUID()}")
            .build()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
    }

    private fun createValidPngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun createValidJpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    @Test
    fun putAndRetrieve_validPng_decodesToBitmap() {
        val validPng = createValidPngBytes()
        val thumbBox = store.boxFor(ThumbCache::class.java)

        val thumb = ThumbCache(data = validPng, checkedServer = true)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        assertNotNull("retrieved ThumbCache should not be null", retrieved)
        assertNotNull("retrieved data should not be null", retrieved!!.data)

        val bitmap = BitmapFactory.decodeByteArray(
            retrieved.data, 0, retrieved.data!!.size
        )
        assertNotNull("should decode to a valid Bitmap", bitmap)
        assertEquals("decoded bitmap width", 1, bitmap!!.width)
        assertEquals("decoded bitmap height", 1, bitmap.height)
        assertEquals(
            "decoded pixel should be red",
            Color.RED, bitmap.getPixel(0, 0)
        )
        bitmap.recycle()
    }

    @Test
    fun putAndRetrieve_validJpeg_decodesToBitmap() {
        val validJpeg = createValidJpegBytes()
        val thumbBox = store.boxFor(ThumbCache::class.java)

        val thumb = ThumbCache(data = validJpeg, checkedServer = true)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        val bitmap = BitmapFactory.decodeByteArray(
            retrieved!!.data, 0, retrieved.data!!.size
        )
        assertNotNull("JPEG should decode to valid Bitmap", bitmap)
        assertEquals(10, bitmap!!.width)
        assertEquals(10, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun notExists_isTrue_whenDataNullAndChecked() {
        val thumbBox = store.boxFor(ThumbCache::class.java)
        val thumb = ThumbCache(data = null, checkedServer = true)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        assertTrue("notExists should be true when data=null and checkedServer=true",
            retrieved!!.notExists)
    }

    @Test
    fun notExists_isFalse_whenDataNotNull() {
        val validPng = createValidPngBytes()
        val thumbBox = store.boxFor(ThumbCache::class.java)
        val thumb = ThumbCache(data = validPng, checkedServer = true)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        assertFalse("notExists should be false when data is present",
            retrieved!!.notExists)
    }

    @Test
    fun emptyByteArray_decodesToNull() {
        val thumbBox = store.boxFor(ThumbCache::class.java)
        val thumb = ThumbCache(data = byteArrayOf(), checkedServer = true)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        val bitmap = BitmapFactory.decodeByteArray(
            retrieved!!.data, 0, retrieved.data!!.size
        )
        assertNull("empty byte array should not decode to a Bitmap", bitmap)
    }

    @Test
    fun corruptData_decodesToNull() {
        val corruptBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val thumbBox = store.boxFor(ThumbCache::class.java)
        val thumb = ThumbCache(data = corruptBytes, checkedServer = true)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        val bitmap = BitmapFactory.decodeByteArray(
            retrieved!!.data, 0, retrieved.data!!.size
        )
        assertNull("corrupt data should not decode to a Bitmap", bitmap)
    }

    @Test
    fun nullData_decodesToNull() {
        val thumbBox = store.boxFor(ThumbCache::class.java)
        val thumb = ThumbCache(data = null, checkedServer = false)
        thumbBox.put(thumb)

        val retrieved = thumbBox[thumb.id]
        assertNull("null data byte array", retrieved!!.data)

        try {
            BitmapFactory.decodeByteArray(retrieved.data, 0, 0)
            fail("Expected NullPointerException for null data")
        } catch (e: NullPointerException) {
        }
    }

    @Test
    fun fsProvider_cacheHit_returnsValidBitmap() {
        val docId = "thumb-instr-${System.nanoTime()}"
        val validPng = createValidPngBytes()

        val thumb = ThumbCache(data = validPng, checkedServer = true)
        appThumbBox().put(thumb)

        val itemBox = ObjectBox.store.boxFor(ItemRecord::class.java)
        val item = ItemRecord(
            documentId = docId, name = "test-thumb.mp3", mimeType = "audio/mpeg",
            displayName = "Test Thumb", lastModified = System.currentTimeMillis(), size = 1234L,
            duration = null, year = null, title = null, album = null,
            track = null, artist = null, bitrate = null, albumId = null, albumCoverTag = null,
            thumbCacheId = thumb.id,
        )
        item.thumbCache.target = thumb
        itemBox.put(item)

        val vf = VirtualFile(
            documentId = docId, libId = "thumb-lib", serverId = 1L, albumId = null,
        )
        vf.item.target = item
        appVirtualFileBox().put(vf)

        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val vpath = VPath.File("user-1", "thumb-lib", null, docId)

            val result = FSProvider.run {
                context.thumbnailFromCacheOrRemote(vpath, null)
            }
            assertNotNull("thumbnailFromCacheOrRemote should return data on cache hit", result)

            val bitmap = BitmapFactory.decodeByteArray(result, 0, result!!.size)
            assertNotNull("returned data should decode to valid Bitmap", bitmap)
            assertEquals(1, bitmap!!.width)
            assertEquals(1, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            bitmap.recycle()
        } finally {
            removeVirtualFileByDocumentId(docId)
            appThumbBox().remove(thumb.id)
            itemBox.remove(item.id)
        }
    }

    @Test
    fun fsProvider_notFoundFile_returnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = FSProvider.run {
            context.thumbnailFromCacheOrRemote(
                VPath.File("user-1", "no-lib", null, "non-existent-doc-${System.nanoTime()}"),
                null
            )
        }
        assertNull("should return null for unknown file", result)
    }

    @Test
    fun fsProvider_notExistsThumb_returnsNull() {
        val docId = "thumb-instr-ne-${System.nanoTime()}"

        val thumb = ThumbCache(data = null, checkedServer = true)
        appThumbBox().put(thumb)

        val itemBox = ObjectBox.store.boxFor(ItemRecord::class.java)
        val item = ItemRecord(
            documentId = docId, name = "nonexists.mp3", mimeType = "audio/mpeg",
            displayName = "No Thumb", lastModified = System.currentTimeMillis(), size = 100L,
            duration = null, year = null, title = null, album = null,
            track = null, artist = null, bitrate = null, albumId = null, albumCoverTag = null,
            thumbCacheId = thumb.id,
        )
        item.thumbCache.target = thumb
        itemBox.put(item)

        val vf = VirtualFile(
            documentId = docId, libId = "thumb-lib", serverId = 1L, albumId = null,
        )
        vf.item.target = item
        appVirtualFileBox().put(vf)

        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val result = FSProvider.run {
                context.thumbnailFromCacheOrRemote(
                    VPath.File("user-1", "thumb-lib", null, docId), null
                )
            }
            assertNull("should return null when thumbnail notExists", result)
        } finally {
            removeVirtualFileByDocumentId(docId)
            appThumbBox().remove(thumb.id)
            itemBox.remove(item.id)
        }
    }

    private fun appThumbBox() =
        ObjectBox.store.boxFor(ThumbCache::class.java)

    private fun appVirtualFileBox() =
        ObjectBox.store.boxFor(VirtualFile::class.java)

    private fun removeVirtualFileByDocumentId(docId: String) {
        val q = appVirtualFileBox().query()
            .equal(
                VirtualFile_.documentId, docId,
                io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE
            ).build()
        val ids = q.findIds()
        if (ids.isNotEmpty()) {
            appVirtualFileBox().removeByIds(ids.toList())
        }
    }
}
