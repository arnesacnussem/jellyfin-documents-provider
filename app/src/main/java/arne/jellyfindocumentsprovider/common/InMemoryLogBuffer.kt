package arne.jellyfindocumentsprovider.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import logcat.LogPriority
import logcat.LogcatLogger
import java.util.concurrent.atomic.AtomicLong

data class LogEntry(
    val id: Long = nextId.getAndIncrement(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogPriority,
    val message: String,
) {
    val formattedTime: String
        get() {
            val s = timestamp / 1000
            val ms = (timestamp % 1000).toInt()
            val h = (s / 3600) % 24
            val m = (s / 60) % 60
            val sec = s % 60
            return "${h.toInt().pad(2)}:${m.toInt().pad(2)}:${sec.toInt().pad(2)}.${ms.pad(3)}"
        }

    private fun Int.pad(width: Int): String = toString().padStart(width, '0')
}

private val nextId = AtomicLong(0)

object InMemoryLogBuffer : LogcatLogger {

    private const val MAX_ENTRIES = 500
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val _newEntryFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 64)
    val newEntryFlow: SharedFlow<LogEntry> = _newEntryFlow

    private val _clearEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearEvents: SharedFlow<Unit> = _clearEvents

    private var uiLogLevel: LogPriority = LogPriority.INFO

    fun setUiLogLevel(level: LogPriority) {
        uiLogLevel = level
    }

    fun snapshot(): List<LogEntry> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        _clearEvents.tryEmit(Unit)
    }

    override fun isLoggable(priority: LogPriority): Boolean = true

    override fun log(priority: LogPriority, tag: String, message: String) {
        android.util.Log.println(priority.priorityInt, tag, message)
        if (priority.priorityInt >= uiLogLevel.priorityInt) {
            val entry = LogEntry(
                tag = tag,
                level = priority,
                message = message
            )
            synchronized(buffer) {
                if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
                buffer.addLast(entry)
            }
            _newEntryFlow.tryEmit(entry)
        }
    }
}
