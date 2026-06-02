package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import arne.jellyfindocumentsprovider.vfs.ThumbCache
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ThumbCacheRepositoryTest {
    private lateinit var store: BoxStore
    private lateinit var repo: ThumbCacheRepository

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
        repo = ObjectBoxThumbCacheRepository(store.boxFor(ThumbCache::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun put_persistsData() {
        val thumb = ThumbCache(data = byteArrayOf(1, 2, 3), checkedServer = true)
        repo.put(thumb)
        assertNotEquals(0, thumb.id)
    }

    @Test
    fun notExists_defaultFalse() {
        val thumb = ThumbCache()
        assertFalse(thumb.notExists)
    }

    @Test
    fun notExists_noDataButChecked() {
        val thumb = ThumbCache(data = null, checkedServer = true)
        assertTrue(thumb.notExists)
    }

    @Test
    fun notExists_hasData() {
        val thumb = ThumbCache(data = byteArrayOf(1), checkedServer = false)
        assertFalse(thumb.notExists)
    }
}
