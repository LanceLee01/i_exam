package com.examhelper.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object ExtractedTextBus {

    var lastTokensPerSec: Float = 0f
    var lastPromptTokens: Int = 0
    var lastTtftMs: Long = 0L

    sealed class Event {
        data class TextExtracted(val text: String) : Event()
        data object RequestExtract : Event()
        data class ClickAnswer(val answer: String, val sourceText: String) : Event()
        data object AccessibilityConnected : Event()
        data object AccessibilityDisconnected : Event()
    }

    sealed class SidebarState {
        data object Idle : SidebarState()
        data class Loading(val message: String, val startTimeMs: Long = 0L, val maxTokens: Int = 2048) : SidebarState()
        data class Preview(val text: String) : SidebarState()
        data class Streaming(val text: String, val partialAnswer: String, val progress: Float, val startTimeMs: Long, val maxTokens: Int = 2048) : SidebarState()
        data class Answering(val text: String) : SidebarState()
        data class Done(val text: String, val answer: String, val kbUsed: Boolean = false) : SidebarState()
        data class Error(val message: String) : SidebarState()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _sidebarState = MutableStateFlow<SidebarState>(SidebarState.Idle)
    val sidebarState = _sidebarState.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected = _accessibilityConnected.asStateFlow()

    fun sendEvent(event: Event) {
        _events.tryEmit(event)
        when (event) {
            is Event.AccessibilityConnected -> _accessibilityConnected.value = true
            is Event.AccessibilityDisconnected -> _accessibilityConnected.value = false
            else -> {}
        }
    }

    fun updateSidebarState(state: SidebarState) {
        _sidebarState.value = state
    }
}
