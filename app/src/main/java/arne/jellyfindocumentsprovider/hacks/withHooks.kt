package arne.jellyfindocumentsprovider.hacks

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend fun <T> withLoading(
    loading: MutableLiveData<Boolean>,
    workingContext: CoroutineContext = Dispatchers.IO,
    block: suspend CoroutineScope.() -> T
): T {
    withContext(Dispatchers.Main) {
        loading.value = true
    }
    val result = withContext(workingContext) {
        block()
    }
    withContext(Dispatchers.Main) {
        loading.value = false
    }

    return result
}