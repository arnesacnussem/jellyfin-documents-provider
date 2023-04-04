package a.sac.jellyfindocumentsprovider

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
