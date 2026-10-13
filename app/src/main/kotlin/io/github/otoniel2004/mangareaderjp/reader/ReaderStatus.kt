package io.github.otoniel2004.mangareaderjp.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live state of the screen reader, shared by the service (writer) and the UI (reader).
 * It lives in memory only: if the process dies, the reader is off anyway.
 */
object ReaderStatus {
    data class State(
        val running: Boolean = false,
        val pagesDetected: Int = 0,
        val captureBlocked: Boolean = false,
        /** Text of each block the OCR found on the last page, in reading order. */
        val lastPageText: List<String> = emptyList(),
        val lastOcrMillis: Long = 0,
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    internal fun update(transform: (State) -> State) = mutableState.update(transform)
}
