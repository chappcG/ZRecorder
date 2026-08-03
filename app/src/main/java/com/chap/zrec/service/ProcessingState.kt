package com.chap.zrec.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProcessingState {

    data class State(
        val active: Boolean = false,
        val minimized: Boolean = false,
        val progress: Int = 0,
        val fileName: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(fileName: String) {
        _state.value = State(active = true, minimized = false, progress = 0, fileName = fileName)
    }

    fun progress(p: Int) {
        _state.value = _state.value.copy(progress = p.coerceIn(0, 99))
    }

    fun minimize() {
        _state.value = _state.value.copy(minimized = true)
    }

    fun show() {
        _state.value = _state.value.copy(minimized = false)
    }

    fun finish() {
        _state.value = State()
    }
}
