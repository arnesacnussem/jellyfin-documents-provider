package arne.jellyfindocumentsprovider.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.findByUUID
import arne.jellyfindocumentsprovider.vfs.toVPath
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.net.URLDecoder

class LyricsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PowerampAPI.Lyrics.ACTION_NEED_LYRICS) return

        val realId = intent.getLongExtra(PowerampAPI.Track.REAL_ID, -1L)
        val rawPath = intent.getStringExtra(PowerampAPI.Track.PATH) ?: run {
            logcat(LogPriority.WARN) { "LyricsReceiver: no PATH extra" }
            return
        }

        if (realId < 0) {
            logcat(LogPriority.WARN) { "LyricsReceiver: invalid REAL_ID=$realId" }
            return
        }

        logcat(LogPriority.DEBUG) { "LyricsReceiver: ACTION_NEED_LYRICS realId=$realId path=$rawPath" }

        val documentId = try {
            resolveDocumentId(rawPath)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "LyricsReceiver: failed to resolve path: $rawPath — ${e.message}" }
            null
        } ?: return

        logcat(LogPriority.DEBUG) { "LyricsReceiver: documentId=$documentId" }

        val vPath = documentId.toVPath()
        if (vPath !is VPath.File) {
            logcat(LogPriority.WARN) { "LyricsReceiver: not a file VPath: $documentId" }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = ObjectBox.server.findByUUID(vPath.rootId)
                if (server == null) {
                    logcat(LogPriority.WARN) { "LyricsReceiver: server not found rootId=${vPath.rootId}" }
                    return@launch
                }
                val api = server.asAccessor(context)
                val lyrics = api.getLyrics(vPath.id)
                logcat(LogPriority.DEBUG) { "LyricsReceiver: lyrics=${lyrics?.take(80) ?: "null"}" }

                val updateIntent = Intent(PowerampAPI.Lyrics.ACTION_UPDATE_LYRICS).apply {
                    putExtra(PowerampAPI.EXTRA_ID, realId)
                    if (lyrics != null) {
                        putExtra(PowerampAPI.Lyrics.EXTRA_LYRICS, lyrics)
                        putExtra(PowerampAPI.Lyrics.EXTRA_INFO_LINE, "Jellyfin Documents Provider")
                    }
                }
                PowerampAPIHelper.sendPAIntent(context, updateIntent)
                logcat(LogPriority.DEBUG) { "LyricsReceiver: ACTION_UPDATE_LYRICS sent via sendPAIntent" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "LyricsReceiver: ${e.message}" }
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
                    logcat(LogPriority.WARN) { "LyricsReceiver: malformed @path: $rawPath" }
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
