package com.musicfeed.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Feed loading state, shared in-process between the service and the UI. */
object FeedStatus {
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Error(val message: String, val needsLogin: Boolean = false) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun set(state: State) {
        _state.value = state
    }
}
