package arne.jellyfindocumentsprovider.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Three state query wrapper
 */
class Query {
    companion object {
        /**
         * Composable function to execute a query. The query is called when the queryTrigger is updated.
         * The result of the query is stored in the data property and the state is updated accordingly.
         * @param context the context to execute the query in (default is Dispatchers.IO)
         * @param query the query to execute
         * @return the state of the query and the data if the state is State.SUCCESS
         *
         */
        @Composable
        fun <T> useQuery(
            context: CoroutineDispatcher = Dispatchers.IO,
            onLoad: (data: T) -> Unit = {},
            query: suspend CoroutineScope.() -> T
        ): Result<T> {
            var state by remember { mutableStateOf(State.PENDING) }
            var queryTrigger by remember { mutableStateOf(false) }
            var data by remember { mutableStateOf<T?>(null) }

            LaunchedEffect(queryTrigger) {
                state = State.FETCHING
                try {
                    data = withContext(context) { query() }
                    state = State.SUCCESS
                    onLoad(data!!)
                } catch (e: Exception) {
                    state = State.ERROR
                    state.message = e.stackTraceToString()
                }

            }

            return Result(
                state = state,
                fetch = { queryTrigger = !queryTrigger },
                data = data
            )
        }
    }

    data class Result<T>(
        val state: State,
        val fetch: () -> Unit,
        val data: T?
    )

    enum class State {
        ERROR, PENDING, FETCHING, SUCCESS;

        var message: String? = null

        val isFetching get() = this == FETCHING
        val isError get() = this == ERROR
        val isSuccess get() = this == SUCCESS
        val isPending get() = this == PENDING

        val isLoaded get() = this == ERROR || this == SUCCESS
        val isLoading get() = this == FETCHING || this == PENDING
    }
}