package com.examhelper.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.examhelper.app.network.Reference

object ExtractedTextBus {

    @Volatile
    var lastTokensPerSec: Float = 0f
    @Volatile
    var lastPromptTokens: Int = 0
    @Volatile
    var lastTtftMs: Long = 0L

    sealed class Event {
        data class TextExtracted(val text: String) : Event()
        data object RequestExtract : Event()
        data class ClickAnswer(val answer: String, val sourceText: String) : Event()
        data class ClickPage(val target: String) : Event()  // "下一页" or "上一页"
        data object AccessibilityConnected : Event()
        data object AccessibilityDisconnected : Event()
    }

    sealed class SidebarState {
        data object Idle : SidebarState()
        data class Loading(val message: String, val startTimeMs: Long = 0L, val maxTokens: Int = 2048) : SidebarState()
        data class Preview(val text: String) : SidebarState()
        data class Streaming(val text: String, val partialAnswer: String, val progress: Float, val startTimeMs: Long, val maxTokens: Int = 2048) : SidebarState()
        data class Answering(val text: String) : SidebarState()
        data class Done(val text: String, val answer: String, val source: AnswerSource = AnswerSource.LLM_DIRECT, val references: List<Reference> = emptyList(), val questionSources: Map<Int, String> = emptyMap()) : SidebarState()
        data class Error(val message: String) : SidebarState()

        // ── 多轮自动答题状态 ──
        enum class MultiPhase { SCANNING, SOLVING, FILLING, DONE, ERROR }

        data class MultiRound(
            val phase: MultiPhase,
            val currentPage: Int = 0,
            val totalPages: Int = 0,
            val progress: Float = 0f,
            val answeredCount: Int = 0,
            val message: String = "",
            val errorMessage: String = ""
        ) : SidebarState()
    }

    enum class AnswerSource(val label: String) {
        EXCEL_MATCH("\uD83D\uDCCB 题库匹配"),
        KB_MATCH("\uD83D\uDCD6 知识库匹配"),
        KB_INFER("\uD83D\uDCD6 知识库推断"),
        SEARCH_MATCH("\uD83D\uDD0D 网络搜索"),
        LLM_DIRECT("\uD83E\uDD16 AI解答")
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
