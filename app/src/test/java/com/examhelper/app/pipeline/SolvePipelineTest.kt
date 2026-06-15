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
    // L1: Excel 题库精准匹配 — score ≥ 0.70 → EXCEL_MATCH early return
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `L1 excel match returns EXCEL_MATCH early when search score is 0 dot 70 or above`() {
        runBlocking {
            // Given: activeKB.search returns an entry with score >= 0.70
            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry("What is capital of France?", "Paris") to 0.85f
            )

            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            val pipeline = SolvePipeline(mockContext)

            // Then: sidebarState immediately becomes Done with EXCEL_MATCH
            ExtractedTextBus.sidebarState.test {
                // Consume initial Idle
                awaitItem()

                pipeline.solve("What is capital of France?")

                val state = awaitItem()
                assertTrue(state is SidebarState.Done, "Expected Done state")
                assertEquals(AnswerSource.EXCEL_MATCH, (state as SidebarState.Done).source)
                assertEquals("Paris", state.answer)

                cancelAndConsumeRemainingEvents()
            }
        }
    }

    @Test
    fun `L1 excel match preserves input text and answer in Done state`() {
        runBlocking {
            val questionText = "What is 2+2?"
            val answerText = "4"

            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry(questionText, answerText) to 0.95f
            )

            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem() // Idle

                pipeline.solve(questionText)

                val state = awaitItem()
                assertTrue(state is SidebarState.Done)
                val done = state as SidebarState.Done
                assertEquals(questionText, done.text)
                assertEquals(answerText, done.answer)
                assertEquals(AnswerSource.EXCEL_MATCH, done.source)

                cancelAndConsumeRemainingEvents()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // L2: Wiki 知识库检索 — Jaccard ≥ 0.50 → KB_MATCH early return
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `L2 KB match returns KB_MATCH early when wiki Jaccard score is 0 dot 50 or above`() {
        runBlocking {
            // L1 miss: activeKB.search returns score < 0.70
            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry("irrelevant", "no match") to 0.30f
            )

            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            // L2: searchByQuestion returns pages with high trigram overlap with query.
            // Title "test question" + summary "test question" produces trigrams
            // that overlap heavily with the query "test question", giving Jaccard ~0.82 >= 0.50.
            val matchingPage = WikiPage(
                title = "test question",
                summary = "test question",
                content = "relevant knowledge content"
            )
            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = listOf(matchingPage),
                ftsPages = listOf(matchingPage),
                trigramPages = emptyList()
            )
            coEvery { anyConstructed<KBEngine>().getAnswerFromKB(any(), any()) } returns "Paris is the capital of France"

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem() // Idle

                pipeline.solve("test question")

                val state = awaitItem()
                assertTrue(state is SidebarState.Done, "Expected Done state")
                assertEquals(AnswerSource.KB_MATCH, (state as SidebarState.Done).source)
                assertEquals("Paris is the capital of France", state.answer)

                cancelAndConsumeRemainingEvents()
            }
        }
    }

    @Test
    fun `L2 KB match preserves input text in Done state`() {
        runBlocking {
            val query = "What is the boiling point of water?"

            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry("irrelevant", "") to 0.25f
            )

            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            val matchingPage = WikiPage(
                title = query,
                summary = query,
                content = "Water boils at 100 degrees Celsius"
            )
            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = listOf(matchingPage),
                ftsPages = listOf(matchingPage),
                trigramPages = emptyList()
            )
            coEvery { anyConstructed<KBEngine>().getAnswerFromKB(any(), any()) } returns "100°C"

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem()

                pipeline.solve(query)

                val state = awaitItem()
                assertTrue(state is SidebarState.Done)
                val done = state as SidebarState.Done
                assertEquals(query, done.text)
                assertEquals(AnswerSource.KB_MATCH, done.source)

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
            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any(), any()) } returns listOf(
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

                pipeline.solve("test question")

                // Must NOT see "正在搜索相关参考资料..." Loading.
                // First state after Idle should be LLM Loading.
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

    // ══════════════════════════════════════════════════════════════════════
    // L4: LLM fallback when both L1 and L2 miss
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `LLM fallback calls chatStream when L1 and L2 miss`() {
        runBlocking {
            // L1 miss
            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry("test", "irrelevant") to 0.30f
            )

            // L2 miss
            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = emptyList(),
                ftsPages = emptyList(),
                trigramPages = emptyList()
            )

            // skip search
            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            // Mock LLM
            mockkConstructor(LLMClient::class)
            every { anyConstructed<LLMClient>().chatStream(any(), any(), any(), any(), any(), any(), any()) } returns flowOf("LLM generated answer")

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem() // Idle

                pipeline.solve("test question")

                // Should go through LLM Loading -> Streaming -> Done
                val loading = awaitItem()
                assertTrue(loading is SidebarState.Loading)
                assertTrue(
                    (loading as SidebarState.Loading).message.contains("LLM"),
                    "Should show LLM loading state"
                )

                val streaming = awaitItem()
                assertTrue(streaming is SidebarState.Streaming)

                val done = awaitItem()
                assertTrue(done is SidebarState.Done)
                val doneState = done as SidebarState.Done
                assertEquals(AnswerSource.LLM_DIRECT, doneState.source)
                assertTrue(
                    doneState.answer.contains("LLM generated answer"),
                    "Answer should contain LLM output"
                )

                cancelAndConsumeRemainingEvents()
            }

            // Verify LLMClient.chatStream was actually invoked
            verify(exactly = 1) { anyConstructed<LLMClient>().chatStream(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `LLM fallback Done state has LLM_DIRECT source when no hints match`() {
        runBlocking {
            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            // score 0.30 < 0.40 -> no Excel hint, L1 miss
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry("test", "no") to 0.30f
            )

            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = emptyList(),
                ftsPages = emptyList(),
                trigramPages = emptyList()
            )

            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            mockkConstructor(LLMClient::class)
            every { anyConstructed<LLMClient>().chatStream(any(), any(), any(), any(), any(), any(), any()) } returns flowOf("answer")

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem()

                pipeline.solve("test question")

                // Skip Loading + Streaming
                awaitItem()
                awaitItem()
                val done = awaitItem()
                assertTrue(done is SidebarState.Done)
                assertEquals(AnswerSource.LLM_DIRECT, (done as SidebarState.Done).source)

                cancelAndConsumeRemainingEvents()
            }
        }
    }

    @Test
    fun `LLM fallback Done state has KB_INFER source when KB hints are present`() {
        runBlocking {
            val mockKB = mockk<KnowledgeBase>()
            mockkObject(KnowledgeBaseManager)
            every { KnowledgeBaseManager.activeKB } returns mockKB
            // Return an entry with score between 0.40 and 0.70 -> hint used but L1 doesn't match
            every { mockKB.search(any(), any()) } returns listOf(
                KBEntry("What is capital?", "Paris") to 0.55f
            )

            coEvery { mockAppConfig.getSnapshot() } returns defaultSnapshot()

            // L2 miss (empty pages)
            coEvery { anyConstructed<KBEngine>().searchByQuestion(any()) } returns SearchResult(
                pages = emptyList(),
                ftsPages = emptyList(),
                trigramPages = emptyList()
            )

            mockkConstructor(LLMClient::class)
            every { anyConstructed<LLMClient>().chatStream(any(), any(), any(), any(), any(), any(), any()) } returns flowOf("Paris answer")

            val pipeline = SolvePipeline(mockContext)

            ExtractedTextBus.sidebarState.test {
                awaitItem()

                pipeline.solve("test question")

                // Skip Loading + Streaming
                awaitItem()
                awaitItem()
                val done = awaitItem()
                assertTrue(done is SidebarState.Done)
                // excelHits with score 0.55 >= 0.40 -> KB_INFER source
                assertEquals(AnswerSource.KB_INFER, (done as SidebarState.Done).source)

                cancelAndConsumeRemainingEvents()
            }
        }
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
