package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.JellyfinServer_
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

class ObjectBoxServerRepository(
    private val box: Box<JellyfinServer>
) : ServerRepository {
    override fun findAll() = box.all

    override fun findByUUID(uuid: String) = box.query {
        equal(JellyfinServer_.uuid, uuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
    }.findFirst()

    override fun findByLibraryId(id: String) = box.all.find { it.library.containsKey(id) }

    override fun count() = box.count()

    override fun put(server: JellyfinServer) {
        box.put(server)
    }

    override fun removeById(id: Long) {
        box.remove(id)
    }
}
