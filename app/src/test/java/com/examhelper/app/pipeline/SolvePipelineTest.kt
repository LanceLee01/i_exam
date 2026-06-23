package com.examhelper.app.pipeline

import android.content.Context
import app.cash.turbine.test
import com.examhelper.app.ExamApplication
import com.examhelper.app.data.AppConfig
import com.examhelper.app.data.ConfigSnapshot
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBase
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.knowledge.SearchResult
import com.examhelper.app.knowledge.db.WikiPage
import com.examhelper.app.network.LLMClient
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.AnswerSource
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SolvePipelineTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockAppConfig = mockk<AppConfig>()
    private val mockApplication = mockk<ExamApplication>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // Reset ExtractedTextBus singleton state before each test
        ExtractedTextBus.updateSidebarState(SidebarState.Idle)

        // Mock KBEngine constructor to prevent real Room DB init
        mockkConstructor(KBEngine::class)

        // Mock ExamApplication.Companion.instance
        mockkObject(ExamApplication.Companion)
        every { ExamApplication.instance } returns mockApplication
        every { mockApplication.appConfig } returns mockAppConfig
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Loading feedback
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `solve emits loading state before slow knowledge base search`() {
        runBlocking {
            val mockKB = mockk<KnowledgeBase>(relaxed = true)
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any<String>(), any<String>(), any<Int>()) } answers {
                Thread.sleep(200)
                emptyList()
            }
            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()
            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = emptyList(),
                ftsPages = emptyList(),
                trigramPages = emptyList()
            )
            mockkConstructor(LLMClient::class)
            every { anyConstructed<LLMClient>().chatStream(any(), any(), any(), any(), any(), any(), any()) } returns flowOf("[1] A")

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem()

                pipeline.solve("1、Question 1?\nA. yes\nB. no")

                val firstState = awaitItem()
                assertTrue(firstState is SidebarState.Loading)
                assertEquals("正在准备解答...", (firstState as SidebarState.Loading).message)

                cancelAndConsumeRemainingEvents()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // L3: Search skip when tavilyApiKey is blank
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `search skip when tavilyApiKey is blank does not emit search loading state`() {
        runBlocking {
            // L1 miss
            val mockKB = mockk<KnowledgeBase>(relaxed = true)
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any<String>(), any<String>(), any<Int>()) } returns listOf(
                KBEntry("test", "irrelevant") to 0.30f
            )

            // L2 miss: empty pages -> wikiTopScore = 0f
            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = emptyList(),
                ftsPages = emptyList(),
                trigramPages = emptyList()
            )

            // tavilyApiKey is blank in defaultSnapshot -> search skipped
            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            // Mock LLM to avoid real network
            mockkConstructor(LLMClient::class)
            every { anyConstructed<LLMClient>().chatStream(any(), any(), any(), any(), any(), any(), any()) } returns flowOf("answer")

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem() // Idle

                pipeline.solve("1、test question")

                // Consume initial Loading("正在准备解答...")
                awaitItem()
                // Next state should be LLM Loading
                val loading = awaitItem()
                assertTrue(loading is SidebarState.Loading)
                val loadingMsg = (loading as SidebarState.Loading).message
                assertFalse(
                    loadingMsg.contains("搜索"),
                    "Should NOT emit search loading state when tavilyApiKey is blank"
                )

                // Consume remaining states
                awaitItem() // Streaming
                val done = awaitItem()
                assertTrue(done is SidebarState.Done)
                // Source must NOT be SEARCH_MATCH since search was skipped
                assertTrue(
                    (done as SidebarState.Done).source != AnswerSource.SEARCH_MATCH,
                    "Final source should not be SEARCH_MATCH when search is skipped"
                )

                cancelAndConsumeRemainingEvents()
            }
        }
    }

    // ── formatCombinedAnswer ──

    @Test
    fun `formatCombinedAnswer sorts by question number ascending`() {
        val l4 = mapOf(3 to "C", 1 to "A")
        val l1 = mapOf(2 to "B")
        val result = SolvePipeline.formatCombinedAnswer(l4, l1)
        assertEquals("[1] A\n[2] B\n[3] C", result)
    }

    @Test
    fun `formatCombinedAnswer L1 overrides L4 on same question`() {
        val l4 = mapOf(1 to "B")
        val l1 = mapOf(1 to "A")
        val result = SolvePipeline.formatCombinedAnswer(l4, l1)
        assertEquals("[1] A", result)
    }

    @Test
    fun `formatCombinedAnswer empty maps produce empty string`() {
        val l4 = emptyMap<Int, String>()
        val l1 = emptyMap<Int, String>()
        val result = SolvePipeline.formatCombinedAnswer(l4, l1)
        assertEquals("", result)
    }

    @Test
    fun `formatCombinedAnswer handles true-false answers`() {
        val l4 = mapOf(1 to "正确", 2 to "错误")
        val l1 = emptyMap<Int, String>()
        val result = SolvePipeline.formatCombinedAnswer(l4, l1)
        assertEquals("[1] 正确\n[2] 错误", result)
    }

    @Test
    fun `formatCombinedAnswer handles multi-letter answers`() {
        val l4 = mapOf(1 to "A B C", 2 to "D E")
        val l1 = emptyMap<Int, String>()
        val result = SolvePipeline.formatCombinedAnswer(l4, l1)
        assertEquals("[1] A B C\n[2] D E", result)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private fun defaultSnapshot() = ConfigSnapshot(
        apiEndpoint = "https://test.api.com",
        apiKey = "test-key",
        tavilyApiKey = "",
        modelName = "test-model",
        temperature = 0.3f,
        maxTokens = 2048,
        systemPrompt = "test prompt",
        watermarkKeywords = emptySet()
    )
}
