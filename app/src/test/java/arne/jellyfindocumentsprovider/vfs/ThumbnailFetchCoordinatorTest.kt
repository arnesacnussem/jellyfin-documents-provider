package arne.jellyfindocumentsprovider.vfs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ThumbnailFetchCoordinatorTest {
    @Test
    fun fetch_differentKeys_runInParallel() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            ThumbnailFetchCoordinator.fetch("thumb-a") {
                firstStarted.complete(Unit)
                release.await()
                byteArrayOf(1)
            }
        }
        val second = async {
            ThumbnailFetchCoordinator.fetch("thumb-b") {
                secondStarted.complete(Unit)
                release.await()
                byteArrayOf(2)
            }
        }

        firstStarted.await()
        secondStarted.await()
        release.complete(Unit)

        assertArrayEquals(byteArrayOf(1), first.await())
        assertArrayEquals(byteArrayOf(2), second.await())
    }

    @Test
    fun fetch_sameKey_coalescesInFlightRequest() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            ThumbnailFetchCoordinator.fetch("thumb-same") {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                byteArrayOf(42)
            }
        }
        started.await()

        val second = async {
            ThumbnailFetchCoordinator.fetch("thumb-same") {
                calls.incrementAndGet()
                byteArrayOf(0)
            }
        }

        release.complete(Unit)

        assertArrayEquals(byteArrayOf(42), first.await())
        assertArrayEquals(byteArrayOf(42), second.await())
        assertEquals(1, calls.get())
    }
}
