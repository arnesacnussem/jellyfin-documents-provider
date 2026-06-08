package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import arne.jellyfindocumentsprovider.data.AppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DownloadLatencyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun measureFirstByteLatency_fullFile() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        val factory = accessor.getDownloadStreamFactory(itemId)

        val durations = mutableListOf<Long>()
        val firstByteDurations = mutableListOf<Long>()

        repeat(5) { iteration ->
            val t0 = System.currentTimeMillis()

            val stream = factory(0, null)

            val t1 = System.currentTimeMillis()

            val buf = ByteArray(1)
            val bytesRead = stream.inputStream.read(buf)

            val t2 = System.currentTimeMillis()

            val headerLatency = t1 - t0
            val firstByteLatency = t2 - t0

            durations.add(headerLatency)
            firstByteDurations.add(firstByteLatency)

            Log.i("DownloadLatency", "iter=$iteration headerLat=${headerLatency}ms firstByteLat=${firstByteLatency}ms range=bytes=0- status=${stream.length} bytesRead=$bytesRead")

            stream.inputStream.close()
        }

        Log.i("DownloadLatency", "=== FULL FILE (bytes=0-) ===")
        Log.i("DownloadLatency", "Header latencies: ${durations.joinToString()} avg=${durations.average().toLong()}ms")
        Log.i("DownloadLatency", "FirstByte latencies: ${firstByteDurations.joinToString()} avg=${firstByteDurations.average().toLong()}ms")

        assertFirstByteAcceptable(firstByteDurations)
    } }

    @Test
    fun measureFirstByteLatency_smallRange() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        val factory = accessor.getDownloadStreamFactory(itemId)

        val durations = mutableListOf<Long>()
        val firstByteDurations = mutableListOf<Long>()

        repeat(5) { iteration ->
            val t0 = System.currentTimeMillis()

            val stream = factory(0, 65535)

            val t1 = System.currentTimeMillis()

            val buf = ByteArray(1)
            val bytesRead = stream.inputStream.read(buf)

            val t2 = System.currentTimeMillis()

            val headerLatency = t1 - t0
            val firstByteLatency = t2 - t0

            durations.add(headerLatency)
            firstByteDurations.add(firstByteLatency)

            Log.i("DownloadLatency", "iter=$iteration headerLat=${headerLatency}ms firstByteLat=${firstByteLatency}ms range=bytes=0-65535 status=${stream.length} bytesRead=$bytesRead")

            stream.inputStream.close()
        }

        Log.i("DownloadLatency", "=== SMALL RANGE (bytes=0-65535) ===")
        Log.i("DownloadLatency", "Header latencies: ${durations.joinToString()} avg=${durations.average().toLong()}ms")
        Log.i("DownloadLatency", "FirstByte latencies: ${firstByteDurations.joinToString()} avg=${firstByteDurations.average().toLong()}ms")

        assertFirstByteAcceptable(firstByteDurations)
    } }

    @Test
    fun measureSequentialLatencyDegradation() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        val factory = accessor.getDownloadStreamFactory(itemId)

        Log.i("DownloadLatency", "=== SEQUENTIAL (10 requests, bytes=0-65535) ===")

        repeat(10) { iteration ->
            val t0 = System.currentTimeMillis()

            val stream = factory(0, 65535)

            val t1 = System.currentTimeMillis()

            val buf = ByteArray(1)
            val bytesRead = stream.inputStream.read(buf)

            val t2 = System.currentTimeMillis()

            val headerLatency = t1 - t0
            val firstByteLatency = t2 - t0

            Log.i("DownloadLatency", "seq iter=$iteration headerLat=${headerLatency}ms firstByteLat=${firstByteLatency}ms bytesRead=$bytesRead")

            // drain the channel to avoid resource leak
            stream.inputStream.close()
        }
    } }

    @Test
    fun measureColdStartVsWarm() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        val factory = accessor.getDownloadStreamFactory(itemId)

        // Cold: first request
        val t0cold = System.currentTimeMillis()
        val stream1 = factory(0, 65535)
        val t1cold = System.currentTimeMillis()
        val buf1 = ByteArray(1)
        stream1.inputStream.read(buf1)
        val t2cold = System.currentTimeMillis()
        stream1.inputStream.close()

        Log.i("DownloadLatency", "COLD headerLat=${t1cold - t0cold}ms firstByteLat=${t2cold - t0cold}ms")

        // Warm: immediately issue a second request
        val t0warm = System.currentTimeMillis()
        val stream2 = factory(0, 65535)
        val t1warm = System.currentTimeMillis()
        val buf2 = ByteArray(1)
        stream2.inputStream.read(buf2)
        val t2warm = System.currentTimeMillis()
        stream2.inputStream.close()

        Log.i("DownloadLatency", "WARM headerLat=${t1warm - t0warm}ms firstByteLat=${t2warm - t0warm}ms")
        Log.i("DownloadLatency", "=== COLD vs WARM: cold=${t2cold - t0cold}ms warm=${t2warm - t0warm}ms ===")

        // Warm should be <= cold (might be same if server has no caching)
        // Just log the difference — don't assert since server config is unknown
    } }

    @Test
    fun measureRangeEndOfFile() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        // First get file size via metadata
        val files = AppDependencies.repos.virtualFile.findAll()
        val vf = files.firstOrNull { it.documentId == itemId && it.serverId == accessor.credential.id }
        assertNotNull("file not found in repo", vf)

        val size = vf!!.item.target.size
        assertTrue("file size must be > 0", size > 0)

        // Request last 64KB
        val start = (size - 65536).coerceAtLeast(0)
        val factory = accessor.getDownloadStreamFactory(itemId)

        Log.i("DownloadLatency", "=== END-OF-FILE (bytes=$start-${size - 1}, fileSize=$size) ===")

        repeat(3) { iteration ->
            val t0 = System.currentTimeMillis()
            val stream = factory(start, size - 1)
            val t1 = System.currentTimeMillis()
            val buf = ByteArray(1)
            stream.inputStream.read(buf)
            val t2 = System.currentTimeMillis()

            Log.i("DownloadLatency", "eof iter=$iteration headerLat=${t1 - t0}ms firstByteLat=${t2 - t0}ms range=bytes=$start-${size - 1}")
            stream.inputStream.close()
        }
    } }

    @Test
    fun measureNoRange() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        val factory = accessor.downloadWithoutRange(itemId)

        val durations = mutableListOf<Long>()
        val firstByteDurations = mutableListOf<Long>()

        repeat(5) { iteration ->
            val t0 = System.currentTimeMillis()

            val stream = factory(0, null)

            val t1 = System.currentTimeMillis()

            val buf = ByteArray(1)
            val bytesRead = stream.inputStream.read(buf)

            val t2 = System.currentTimeMillis()

            val headerLatency = t1 - t0
            val firstByteLatency = t2 - t0

            durations.add(headerLatency)
            firstByteDurations.add(firstByteLatency)

            Log.i("DownloadLatency", "noRange iter=$iteration headerLat=${headerLatency}ms firstByteLat=${firstByteLatency}ms status=${stream.length} bytesRead=$bytesRead")

            stream.inputStream.close()
        }

        Log.i("DownloadLatency", "=== NO RANGE (bare GET, no Range header) ===")
        Log.i("DownloadLatency", "Header latencies: ${durations.joinToString()} avg=${durations.average().toLong()}ms")
        Log.i("DownloadLatency", "FirstByte latencies: ${firstByteDurations.joinToString()} avg=${firstByteDurations.average().toLong()}ms")

        assertFirstByteAcceptable(firstByteDurations)
    } }

    @Test
    fun measureComparisonAllThree() { runBlocking {
        val accessor = getFirstAccessor()
        val itemId = getFirstAudioItemId(accessor.credential.id)

        val noRangeFactory = accessor.downloadWithoutRange(itemId)
        val smallRangeFactory = accessor.getDownloadStreamFactory(itemId) // bytes=0-65535
        val fullRangeFactory = accessor.getDownloadStreamFactory(itemId)   // bytes=0-

        Log.i("DownloadLatency", "=== COMPARISON: noRange vs smallRange vs fullRange ===")

        // noRange
        val t0nr = System.currentTimeMillis()
        val sNr = noRangeFactory(0, null)
        val t1nr = System.currentTimeMillis()
        val buf = ByteArray(1)
        sNr.inputStream.read(buf)
        val t2nr = System.currentTimeMillis()
        sNr.inputStream.close()
        Log.i("DownloadLatency", "noRange  headerLat=${t1nr - t0nr}ms firstByteLat=${t2nr - t0nr}ms status=${sNr.length}")

        // smallRange
        val t0sr = System.currentTimeMillis()
        val sSr = smallRangeFactory(0, 65535)
        val t1sr = System.currentTimeMillis()
        sSr.inputStream.read(buf)
        val t2sr = System.currentTimeMillis()
        sSr.inputStream.close()
        Log.i("DownloadLatency", "smallRng headerLat=${t1sr - t0sr}ms firstByteLat=${t2sr - t0sr}ms status=${sSr.length}")

        // fullRange
        val t0fr = System.currentTimeMillis()
        val sFr = fullRangeFactory(0, null)
        val t1fr = System.currentTimeMillis()
        sFr.inputStream.read(buf)
        val t2fr = System.currentTimeMillis()
        sFr.inputStream.close()
        Log.i("DownloadLatency", "fullRng  headerLat=${t1fr - t0fr}ms firstByteLat=${t2fr - t0fr}ms status=${sFr.length}")

        Log.i("DownloadLatency", "SUMMARY: noRange=${t2nr - t0nr}ms smallRange=${t2sr - t0sr}ms fullRange=${t2fr - t0fr}ms")
    } }

    private fun getFirstAccessor(): JellyfinAccessor {
        val servers = AppDependencies.repos.server.findAll()
        assertTrue("no servers configured", servers.isNotEmpty())
        val server = servers.first()
        return JellyfinAccessor(context, server)
    }

    private fun getFirstAudioItemId(serverObjectBoxId: Long): String {
        val files = AppDependencies.repos.virtualFile.findAll()
        val audioFile = files.firstOrNull { it.serverId == serverObjectBoxId }
        if (audioFile == null) {
            fail("no audio files found for server id=$serverObjectBoxId")
        }
        val file = audioFile!!
        Log.i("DownloadLatency", "Using item: ${file.documentId} name=${file.item.target.name} size=${file.item.target.size}")
        return file.documentId
    }

    private fun assertFirstByteAcceptable(latencies: List<Long>) {
        val avg = latencies.average().toLong()
        Log.i("DownloadLatency", "First-byte avg=${avg}ms over ${latencies.size} iterations")
        // If any single request exceeds 15s, fail
        for ((i, lat) in latencies.withIndex()) {
            assertTrue("iteration $i first-byte latency ${lat}ms exceeds 15000ms", lat < 15_000)
        }
    }
}
