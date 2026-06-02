package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import arne.jellyfindocumentsprovider.common.encryptedPrefs

object JellyfinTokenStore {
    private fun key(serverUuid: String) = "jellyfin_token_$serverUuid"

    fun save(context: Context, serverUuid: String, token: String) {
        if (token.isBlank()) return
        context.applicationContext.encryptedPrefs()
            .edit()
            .putString(key(serverUuid), token)
            .apply()
    }

    fun resolve(context: Context, server: JellyfinServer): String {
        val prefs = context.applicationContext.encryptedPrefs()
        val encryptedToken = prefs.getString(key(server.uuid), null)
        if (!encryptedToken.isNullOrBlank()) return encryptedToken

        if (server.token.isNotBlank()) {
            save(context, server.uuid, server.token)
            return server.token
        }

        return ""
    }

    fun migrate(context: Context, servers: List<JellyfinServer>, saveServer: (JellyfinServer) -> Unit) {
        servers.filter { it.token.isNotBlank() }.forEach { server ->
            save(context, server.uuid, server.token)
            saveServer(server.copy(token = ""))
        }
    }
}
