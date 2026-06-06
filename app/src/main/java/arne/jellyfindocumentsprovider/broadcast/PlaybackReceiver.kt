package arne.jellyfindocumentsprovider.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.findByUUID
import arne.jellyfindocumentsprovider.vfs.toVPath
import com.maxmpz.poweramp.player.PowerampAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.net.URLDecoder
import java.util.UUID

class PlaybackReceiver : BroadcastReceiver() {
    companion object {
        @Volatile
        private var currentItemId: String? = null
        @Volatile
        private var currentPlaySessionId: String? = null
        @Volatile
        private var currentRootId: String? = null
        @Volatile
        private var isPaused: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    PowerampAPI.ACTION_TRACK_CHANGED_EXPLICIT -> onTrackChanged(context, intent)
                    PowerampAPI.ACTION_STATUS_CHANGED_EXPLICIT -> onStatusChanged(context, intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun onTrackChanged(context: Context, intent: Intent) {
        val track = intent.getBundleExtra(PowerampAPI.EXTRA_TRACK) ?: run {
            logcat(LogPriority.WARN) { "PlaybackReceiver: no track bundle" }
            return
        }
        val rawPath = track.getString(PowerampAPI.Track.PATH) ?: run {
            logcat(LogPriority.WARN) { "PlaybackReceiver: no PATH in track bundle" }
            return
        }

        val documentId = resolveDocumentId(rawPath) ?: return
        val vPath = documentId.toVPath()
        if (vPath !is VPath.File) {
            logcat(LogPriority.WARN) { "PlaybackReceiver: not a file VPath: $documentId" }
            return
        }

        val rootId = vPath.rootId
        val server = ObjectBox.server.findByUUID(rootId) ?: run {
            logcat(LogPriority.WARN) { "PlaybackReceiver: server not found rootId=$rootId" }
            return
        }

        val itemId = vPath.id

        currentItemId?.let { oldId ->
            currentPlaySessionId?.let { oldSession ->
                try {
                    JellyfinAccessor(context, server).reportPlaybackStopped(oldId, oldSession)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "PlaybackReceiver: stop previous failed: ${e.message}" }
                }
            }
        }

        val sessionId = UUID.randomUUID().toString()
        currentItemId = itemId
        currentPlaySessionId = sessionId
        currentRootId = rootId
        isPaused = false

        logcat(LogPriority.DEBUG) { "PlaybackReceiver: track changed -> item=$itemId session=$sessionId" }

        try {
            JellyfinAccessor(context, server).reportPlaybackStart(itemId, sessionId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "PlaybackReceiver: start failed: ${e.message}" }
        }
    }

    private suspend fun onStatusChanged(context: Context, intent: Intent) {
        val state = intent.getIntExtra(PowerampAPI.EXTRA_STATE, PowerampAPI.STATE_NO_STATE)
        val posSeconds = intent.getIntExtra(PowerampAPI.EXTRA_POSITION, 0)
        val positionTicks = posSeconds * 10_000_000L

        when (state) {
            PowerampAPI.STATE_STOPPED -> {
                val itemId = currentItemId ?: return
                val session = currentPlaySessionId ?: return
                val rootId = currentRootId ?: return

                logcat(LogPriority.DEBUG) { "PlaybackReceiver: stopped item=$itemId pos=${posSeconds}s" }

                currentItemId = null
                currentPlaySessionId = null
                currentRootId = null
                isPaused = false

                val server = ObjectBox.server.findByUUID(rootId) ?: run {
                    logcat(LogPriority.WARN) { "PlaybackReceiver: server not found rootId=$rootId" }
                    return
                }

                try {
                    JellyfinAccessor(context, server).reportPlaybackStopped(itemId, session, positionTicks)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "PlaybackReceiver: stop failed: ${e.message}" }
                }
            }

            PowerampAPI.STATE_PAUSED -> {
                if (isPaused) return
                val itemId = currentItemId ?: return
                val session = currentPlaySessionId ?: return
                val rootId = currentRootId ?: return

                isPaused = true
                logcat(LogPriority.DEBUG) { "PlaybackReceiver: paused item=$itemId pos=${posSeconds}s" }

                val server = ObjectBox.server.findByUUID(rootId) ?: return
                try {
                    JellyfinAccessor(context, server).reportPlaybackProgress(itemId, session, positionTicks, isPaused = true)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "PlaybackReceiver: pause report failed: ${e.message}" }
                }
            }

            PowerampAPI.STATE_PLAYING -> {
                if (!isPaused) return
                val itemId = currentItemId ?: return
                val session = currentPlaySessionId ?: return
                val rootId = currentRootId ?: return

                isPaused = false
                logcat(LogPriority.DEBUG) { "PlaybackReceiver: resumed item=$itemId pos=${posSeconds}s" }

                val server = ObjectBox.server.findByUUID(rootId) ?: return
                try {
                    JellyfinAccessor(context, server).reportPlaybackProgress(itemId, session, positionTicks, isPaused = false)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "PlaybackReceiver: resume report failed: ${e.message}" }
                }
            }
        }
    }

    private fun resolveDocumentId(rawPath: String): String? {
        return when {
            rawPath.startsWith("content://") ->
                DocumentsContract.getDocumentId(android.net.Uri.parse(rawPath))

            rawPath.startsWith("@") -> {
                val slashIdx = rawPath.indexOf('/', 1)
                if (slashIdx < 0) {
                    logcat(LogPriority.WARN) { "PlaybackReceiver: malformed @path: $rawPath" }
                    return null
                }
                val rest = rawPath.substring(slashIdx + 1)
                val lastPart = rest.split('/').last()
                URLDecoder.decode(lastPart, "UTF-8")
            }

            else -> rawPath
        }
    }
}
