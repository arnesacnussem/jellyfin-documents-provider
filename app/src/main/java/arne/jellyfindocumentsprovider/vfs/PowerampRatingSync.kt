package arne.jellyfindocumentsprovider.vfs

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
import logcat.LogPriority
import logcat.logcat
import java.net.URLEncoder

object PowerampRatingSync {

    private const val PA_AUTHORITY = "com.maxmpz.audioplayer.data"
    private val FILES_URI = Uri.parse("content://$PA_AUTHORITY/files")
    private const val RATING_LIKE = 5
    private const val RATING_NOT_SET = 0
    private const val ACTION_SCAN_TAGS = "com.maxmpz.audioplayer.ACTION_SCAN_TAGS"

    private var firstQueryLogged = false

    fun pushAllLikesToPoweramp(context: Context) {
        val startTime = System.currentTimeMillis()
        val allFiles = try {
            ObjectBox.virtualFile.all
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "PowerampRatingSync: cannot access ObjectBox, skipping: ${e.message}" }
            return
        }
        logcat(LogPriority.INFO) { "PowerampRatingSync: pushing ${allFiles.size} items to Poweramp" }

        var updated = 0
        for (vf in allFiles) {
            if (!vf.isFavorite) continue
            val rating = if (vf.isFavorite) RATING_LIKE else RATING_NOT_SET
            if (updatePowerampRating(context, vf.providerId, rating)) {
                updated++
            }
        }
        logcat(LogPriority.INFO) {
            "PowerampRatingSync: pushed $updated/${allFiles.size} items (took ${System.currentTimeMillis() - startTime}ms)"
        }

        if (updated > 0) {
            triggerPowerampRefresh(context)
        }
    }

    private fun triggerPowerampRefresh(context: Context) {
        try {
            val intent = Intent(ACTION_SCAN_TAGS).apply {
                setPackage("com.maxmpz.audioplayer")
                putExtra("provider", "arne.jellyfindocumentsprovider")
            }
            context.sendBroadcast(intent)
            logcat(LogPriority.INFO) { "PowerampRatingSync: triggered Poweramp tag scan to refresh UI" }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "PowerampRatingSync: failed to trigger refresh: ${e.message}" }
        }
    }

    fun sendFavorite(context: Context, isFavorite: Boolean) {
        val intent = Intent(PowerampAPI.ACTION_API_COMMAND).apply {
            putExtra(PowerampAPI.EXTRA_COMMAND,
                if (isFavorite) PowerampAPI.Commands.LIKE
                else PowerampAPI.Commands.UNLIKE
            )
        }
        try {
            PowerampAPIHelper.sendPAIntent(context, intent)
            logcat(LogPriority.DEBUG) { "PowerampRatingSync: API sent ${if (isFavorite) "LIKE" else "UNLIKE"}" }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "PowerampRatingSync: API send like failed: ${e.message}" }
        }
    }

    private fun updatePowerampRating(context: Context, providerId: String, rating: Int): Boolean {
        return try {
            val encodedId = URLEncoder.encode(providerId, "UTF-8")
            val cursor = context.contentResolver.query(
                FILES_URI,
                arrayOf("folder_files._id", "folder_files.file_path"),
                "folder_files.file_path LIKE ?",
                arrayOf("%$encodedId%"),
                null
            )
            if (cursor == null) {
                if (!firstQueryLogged) {
                    firstQueryLogged = true
                    logcat(LogPriority.ERROR) {
                        "PowerampRatingSync: ContentProvider query returned null"
                    }
                }
                return false
            }

            cursor.use { c ->
                if (c.count == 0 && !firstQueryLogged) {
                    firstQueryLogged = true
                    logcat(LogPriority.ERROR) {
                        "PowerampRatingSync: no matching Poweramp file found. providerId=$providerId encoded=$encodedId"
                    }
                }
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val filePath = c.getString(1)
                    val cv = ContentValues().apply {
                        put("rating", rating)
                    }
                    val updated = context.contentResolver.update(
                        FILES_URI,
                        cv,
                        "folder_files._id = ?",
                        arrayOf(id.toString())
                    )
                    logcat(LogPriority.DEBUG) {
                        "PowerampRatingSync: updated rating for id=$id path=$filePath rating=$rating rows=$updated"
                    }
                    return updated > 0
                }
            }
            false
        } catch (e: Exception) {
            if (!firstQueryLogged) {
                firstQueryLogged = true
                logcat(LogPriority.ERROR) {
                    "PowerampRatingSync: exception for $providerId: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
            false
        }
    }
}
