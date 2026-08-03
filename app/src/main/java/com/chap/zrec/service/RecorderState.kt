package com.chap.zrec.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecorderState {

    data class State(
        val active: Boolean = false,
        val paused: Boolean = false,
        val countdown: Int = 0,
        val startedAt: Long = 0L,
        val pausedAt: Long = 0L,
        val accumulatedPause: Long = 0L
    ) {
        fun elapsed(now: Long): Long = when {
            !active -> 0L
            paused -> (pausedAt - startedAt - accumulatedPause).coerceAtLeast(0L)
            else -> (now - startedAt - accumulatedPause).coerceAtLeast(0L)
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setCountdown(value: Int) {
        _state.value = State(active = false, paused = false, countdown = value)
    }

    fun setActive(startedAt: Long) {
        _state.value = State(
            active = true,
            paused = false,
            countdown = 0,
            startedAt = startedAt,
            pausedAt = 0L,
            accumulatedPause = 0L
        )
    }

    fun setPaused(pausedAt: Long) {
        val current = _state.value
        _state.value = current.copy(paused = true, pausedAt = pausedAt)
    }

    fun setResumed(accumulatedPause: Long) {
        val current = _state.value
        _state.value = current.copy(
            paused = false,
            pausedAt = 0L,
            accumulatedPause = accumulatedPause
        )
    }

    fun setInactive() {
        _state.value = State()
    }
}
