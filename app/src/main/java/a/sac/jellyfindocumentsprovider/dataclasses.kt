package a.sac.jellyfindocumentsprovider

data class ServerInfo(
    var baseUrl: String = "",
    var username: String = "",
    var password: String = ""
)

data class MediaLibraryListItem(
    var checked: Boolean,
    val name: String,
    val id: String
)

val Any.TAG: String
    get() {
        return if (!javaClass.isAnonymousClass) {
            javaClass.simpleName
        } else {
            val name = javaClass.name
            if (name.length <= 23) name else name.substring(
                name.length - 23,
                name.length
            )// last 23 chars
        }
    }


fun convertBytesToHumanReadable(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    if (bytes < 1024) {
        return "$bytes ${units[0]}"
    }

    var value = bytes.toDouble()
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }

    return "%.2f ${units[unitIndex]}".format(value)
}

val Long.HumanReadable: String
    get() = convertBytesToHumanReadable(this)
val Int.HumanReadable: String
    get() = convertBytesToHumanReadable(this.toLong())