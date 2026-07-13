package com.examhelper.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object ExtractedTextBus {

    /** 最近一次自动填入中失败的题号和原因，例如 "81: toggle A→正确 NOT FOUND" */
    @Volatile
    var lastToggleFailedQuestions: List<String> = emptyList()

    sealed class Event {
        data class TextExtracted(val text: String) : Event()
        data object RequestExtract : Event()
        data object RequestExtractStatic : Event()  // 不滚动，只读当前可见内容
        data class ClickAnswer(val answer: String, val sourceText: String, val kbAnswerOptions: Map<Int, String> = emptyMap(), val skipKbResolution: Set<Int> = emptySet()) : Event()
        data class ClickPage(val target: String) : Event()  // "下一页" or "上一页"
        data object AccessibilityConnected : Event()
        data object AccessibilityDisconnected : Event()
    }

    sealed class SidebarState {
        data object Idle : SidebarState()
        data class Loading(val message: String, val startTimeMs: Long = 0L) : SidebarState()
        data class Preview(val text: String) : SidebarState()
        data class Done(val text: String, val answer: String, val source: AnswerSource = AnswerSource.EXCEL_MATCH, val questionSources: Map<Int, String> = emptyMap(), val kbAnswerOptions: Map<Int, String> = emptyMap(), val kbQuestionTexts: Map<Int, String> = emptyMap(), val toggleFailedQuestions: List<Int> = emptyList(), val resolvedQuestions: Set<Int> = emptySet(), val kbOriginalAnswers: Map<Int, String> = emptyMap()) : SidebarState()
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
            val errorMessage: String = "",
            /** 当前填入的题目摘要（选项+答案） */
            val currentQuestionSummary: String = ""
        ) : SidebarState()
    }

    enum class AnswerSource(val label: String) {
        EXCEL_MATCH("\uD83D\uDCCB 题库匹配"),
        KB_MATCH("\uD83D\uDCD6 知识库匹配")
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
