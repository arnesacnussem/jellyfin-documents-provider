package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.JellyfinServer

interface ServerRepository {
    fun findAll(): List<JellyfinServer>
    fun findByUUID(uuid: String): JellyfinServer?
    fun findByLibraryId(id: String): JellyfinServer?
    fun count(): Long
    fun put(server: JellyfinServer)
    fun removeById(id: Long)
}
