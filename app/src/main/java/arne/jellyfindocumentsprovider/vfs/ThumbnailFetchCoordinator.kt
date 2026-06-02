package arne.jellyfindocumentsprovider.vfs

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

object ThumbnailFetchCoordinator {
    private val inFlight = ConcurrentHashMap<String, Deferred<ByteArray?>>()

    suspend fun fetch(key: String, fetcher: suspend () -> ByteArray?): ByteArray? = coroutineScope {
        val newRequest = async(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            fetcher()
        }
        val request = inFlight.putIfAbsent(key, newRequest)
        if (request == null) {
            try {
                newRequest.start()
                newRequest.await()
            } finally {
                inFlight.remove(key, newRequest)
            }
        } else {
            newRequest.cancel()
            request.await()
        }
    }
}
