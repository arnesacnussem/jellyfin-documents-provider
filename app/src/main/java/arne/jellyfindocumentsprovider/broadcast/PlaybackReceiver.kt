package arne.jellyfindocumentsprovider.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.SyncLikeToggle
import arne.jellyfindocumentsprovider.common.getEnum
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.findByDocumentId
import arne.jellyfindocumentsprovider.vfs.findByUUID
import arne.jellyfindocumentsprovider.vfs.toVPath
import com.maxmpz.poweramp.player.PowerampAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.net.URLDecoder
import java.net.URLEncoder
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
            logcat(LogPriority.DEBUG) { "PlaybackReceiver: no track bundle" }
            return
        }
        val rawPath = track.getString(PowerampAPI.Track.PATH) ?: run {
            logcat(LogPriority.DEBUG) { "PlaybackReceiver: no PATH in track bundle" }
            return
        }

        val documentId = resolveDocumentId(rawPath) ?: return
        val vPath = documentId.toVPath()
        if (vPath !is VPath.File) {
            logcat(LogPriority.DEBUG) { "PlaybackReceiver: not a file VPath: $documentId" }
            return
        }

        val rootId = vPath.rootId
        val server = ObjectBox.server.findByUUID(rootId) ?: run {
            logcat(LogPriority.DEBUG) { "PlaybackReceiver: server not found rootId=$rootId" }
            return
        }

        val itemId = vPath.id

        currentItemId?.let { oldId ->
            currentPlaySessionId?.let { oldSession ->
                try {
                    JellyfinAccessor(context, server).reportPlaybackStopped(oldId, oldSession)
                } catch (e: Exception) {
                    logcat(LogPriority.DEBUG) { "PlaybackReceiver: stop previous failed: ${e.message}" }
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
            logcat(LogPriority.DEBUG) { "PlaybackReceiver: start failed: ${e.message}" }
        }

        syncRatingWithPoweramp(context, vPath, server)
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
                    logcat(LogPriority.DEBUG) { "PlaybackReceiver: server not found rootId=$rootId" }
                    return
                }

                try {
                    JellyfinAccessor(context, server).reportPlaybackStopped(itemId, session, positionTicks)
                } catch (e: Exception) {
                    logcat(LogPriority.DEBUG) { "PlaybackReceiver: stop failed: ${e.message}" }
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
                    logcat(LogPriority.DEBUG) { "PlaybackReceiver: pause report failed: ${e.message}" }
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
                    logcat(LogPriority.DEBUG) { "PlaybackReceiver: resume report failed: ${e.message}" }
                }
            }
        }
    }

    private fun syncRatingWithPoweramp(context: Context, vPath: VPath.File, server: arne.jellyfindocumentsprovider.vfs.JellyfinServer) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getEnum<SyncLikeToggle>(PrefKeys.SYNC_RATINGS_TO_JELLYFIN) != SyncLikeToggle.ENABLED) return

        val vf = ObjectBox.virtualFile.findByDocumentId(vPath.id, server.id) ?: return

        val rating = try {
            val encodedProviderId = URLEncoder.encode(vf.providerId, "UTF-8")
            context.contentResolver.query(
                android.net.Uri.parse("content://com.maxmpz.audioplayer.data/files"),
                arrayOf("folder_files.rating"),
                "folder_files.file_path LIKE ?",
                arrayOf("%$encodedProviderId%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getInt(0) else null
            }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "PlaybackReceiver: failed to query Poweramp rating: ${e.message}" }
            null
        }

        if (rating == null) return

        val isFavorite = rating == 5
        if (vf.isFavorite == isFavorite) return

        logcat(LogPriority.INFO) {
            "PlaybackReceiver: rating changed for ${vf.item.target.name}: isFavorite=$isFavorite"
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = JellyfinAccessor(context, server)
                if (isFavorite) api.markFavoriteItem(vf.item.target.documentId)
                else api.unmarkFavoriteItem(vf.item.target.documentId)
                ObjectBox.virtualFile.put(vf.copy(isFavorite = isFavorite))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "PlaybackReceiver: failed to update Jellyfin: ${e.message}" }
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
                    logcat(LogPriority.DEBUG) { "PlaybackReceiver: malformed @path: $rawPath" }
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
