package a.sac.jellyfindocumentsprovider.utils

import logcat.LogPriority
import logcat.logcat as squareLogcat

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    tag: String? = null,
    message: () -> String
) {
    squareLogcat(priority = priority, tag = tag, message = message)
}

inline fun Any.logcat(
    tag: String,
    priority: LogPriority? = LogPriority.DEBUG,
    message: () -> String
) {
    squareLogcat(priority = priority!!, tag = tag, message = message)
}


/**
 * typically used for get a shortened version of uuid (8-char)
 */
val String.short: String
    get() = this.substring(0, 8)


/**
 * Return a human readable string
 */
val Long.readable: String
    get() = convertBytesToHumanReadable(this)

/**
 * Return a human readable string
 */
val Int.readable: String
    get() = convertBytesToHumanReadable(this.toLong())
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