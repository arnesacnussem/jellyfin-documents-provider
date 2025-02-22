package arne.jellyfindocumentsprovider.vfs

import org.jellyfin.sdk.model.serializer.toUUID

enum class VPathType {
    USER, LIBRARY, ALBUM, FILE
}

sealed class VPath {
    val type: VPathType
        get() = when (this) {
            is User -> VPathType.USER
            is Library -> VPathType.LIBRARY
            is Album -> VPathType.ALBUM
            is File -> VPathType.FILE
        }

    /**
     * id of current level (user, library, album, file)
     */
    val id
        get() = when (this) {
            is User -> userId
            is Library -> libraryId
            is Album -> albumId
            is File -> fileId
        }

    /**
     * same as userId
     */
    val rootId
        get() = when (this) {
            is User -> id
            is Library -> userId
            is Album -> userId
            is File -> userId
        }

    val uuid get() = id.toUUID()
    fun parent(): VPath? = when (this) {
        is User -> parent
        is Library -> parent
        is Album -> parent
        is File -> parent
    }

    fun tryResolveName(): String? = with(ObjectBox) {
        when (this@VPath) {
            is User -> server.findByUUID(id).let { "${it.name}@${it.serverName}" }
            is Library -> server.findByLibraryId(id).library[id]
            is Album -> albumInfo.findAlbumByUUID(id).firstOrNull()?.name
            is File -> virtualFile.findByDocumentId(id).name
        }
    }

    data class User(val userId: String) : VPath() {
        val parent get() = null

        override fun toString() = userId
        fun library(id: String) = Library(userId, id)
    }

    data class Library(val userId: String, val libraryId: String) : VPath() {
        val parent get() = User(userId)
        override fun toString() = "$userId/$libraryId"
        fun album(id: String) = Album(userId, libraryId, id)
        fun file(id: String) = File(userId, libraryId, null, id)
    }

    data class Album(val userId: String, val libraryId: String, val albumId: String) : VPath() {
        val parent get() = Library(userId, libraryId)
        override fun toString() = "$userId/$libraryId/a_$albumId"
        fun file(id: String) = File(userId, libraryId, albumId, id)
    }

    data class File(
        val userId: String,
        val libraryId: String,
        val albumId: String?,
        val fileId: String
    ) : VPath() {
        val parent
            get() = if (albumId != null) Album(userId, libraryId, albumId) else Library(
                userId,
                libraryId
            )

        override fun toString(): String {
            val base = "$userId/$libraryId"
            return if (albumId != null) {
                "$base/a_$albumId/f_$fileId"
            } else {
                "$base/f_$fileId"
            }
        }
    }

    companion object {
        fun parse(path: String): VPath? {
            val segments = path.split('/')
            require(segments.isNotEmpty()) { "Empty path" }

            if (segments.contains(".nomedia")) return null

            return when (segments.size) {
                1 -> User(segments[0])
                2 -> Library(segments[0], segments[1])
                3 -> when {
                    segments[2].startsWith("f_") -> File(
                        userId = segments[0],
                        libraryId = segments[1],
                        albumId = null,
                        fileId = segments[2].removePrefix("f_")
                    )

                    segments[2].startsWith("a_") -> Album(
                        userId = segments[0],
                        libraryId = segments[1],
                        albumId = segments[2].removePrefix("a_")
                    )

                    else -> null
                }

                4 -> {
                    if (!(segments[2].startsWith("a_") && segments[3].startsWith("f_"))) return null
                    File(
                        userId = segments[0],
                        libraryId = segments[1],
                        albumId = segments[2].removePrefix("a_"),
                        fileId = segments[3].removePrefix("f_")
                    )
                }

                else -> null
            }
        }
    }
}

fun String?.toVPath() = this?.let { VPath.parse(this) }

fun VPath.isParentOf(other: VPath): Boolean = this.parent() == other