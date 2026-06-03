package arne.jellyfindocumentsprovider.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class EventCategory { SYNC, METADATA, NETWORK }

data class StatusEvent(
    val id: String,
    val category: EventCategory,
    val message: String,
    val startTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val progress: Float = -1f,
)

object StatusEventManager {

    private val _events = MutableStateFlow<List<StatusEvent>>(emptyList())
    val events: StateFlow<List<StatusEvent>> = _events
    private val activeEvents = LinkedHashMap<String, StatusEvent>()

    fun startSync(id: String, message: String) = synchronized(this) {
        val event = StatusEvent(id = id, category = EventCategory.SYNC, message = message)
        activeEvents[id] = event
        _events.value = activeEvents.values.toList()
    }

    fun updateSync(id: String, message: String, progress: Float) = synchronized(this) {
        val existing = activeEvents[id] ?: return
        activeEvents[id] = existing.copy(message = message, progress = progress)
        _events.value = activeEvents.values.toList()
    }

    fun finishSync(id: String) = synchronized(this) {
        activeEvents.remove(id)
        _events.value = activeEvents.values.toList()
    }

    fun startMetadata(id: String, message: String) = synchronized(this) {
        val event = StatusEvent(id = id, category = EventCategory.METADATA, message = message)
        activeEvents[id] = event
        _events.value = activeEvents.values.toList()
    }

    fun finishMetadata(id: String) = synchronized(this) {
        activeEvents.remove(id)
        _events.value = activeEvents.values.toList()
    }

    fun startNetwork(id: String, message: String) = synchronized(this) {
        val event = StatusEvent(id = id, category = EventCategory.NETWORK, message = message)
        activeEvents[id] = event
        _events.value = activeEvents.values.toList()
    }

    fun updateNetwork(id: String, message: String, progress: Float) = synchronized(this) {
        val existing = activeEvents[id] ?: return
        activeEvents[id] = existing.copy(message = message, progress = progress)
        _events.value = activeEvents.values.toList()
    }

    fun finishNetwork(id: String) = synchronized(this) {
        activeEvents.remove(id)
        _events.value = activeEvents.values.toList()
    }

    fun activeCount(category: EventCategory): Int = synchronized(this) {
        activeEvents.count { it.value.category == category }
    }
}
