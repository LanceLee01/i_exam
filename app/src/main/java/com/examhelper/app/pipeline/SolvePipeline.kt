package com.examhelper.app.pipeline

import android.content.Context
import android.util.Log
import com.examhelper.app.ExamApplication
import com.examhelper.app.data.ConfigSnapshot
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.knowledge.db.WikiPage
import com.examhelper.app.network.LLMClient
import com.examhelper.app.network.Reference
import com.examhelper.app.network.TavilyClient
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.AnswerSource
import com.examhelper.app.util.ExtractedTextBus.SidebarState

class SolvePipeline(private val context: Context) {

    private val kbEngine = KBEngine(context)

    suspend fun solve(text: String) {
        val requestStartMs = System.currentTimeMillis()
        ExtractedTextBus.updateSidebarState(
            SidebarState.Loading("正在准备解答...", requestStartMs)
        )
        val config = ExamApplication.instance.appConfig.getSnapshot()
        val maxTokens = config.maxTokens

        // L1: Excel KB matching
        val l1Answers = tryExcelMatchAll(text)
        val l1Keys = l1Answers?.keys?.toSet() ?: emptySet()

        // Determine all question numbers
        val allQ = extractQuestionNumbers(text).map { it.first }.toSet()
        val unmatchedQ = (allQ - l1Keys).sorted()

        Log.d(TAG, "solve: L1=${l1Keys.size} allQ=${allQ.size} unmatchedQ=$unmatchedQ l1Keys=$l1Keys")

        // If all matched, return directly
        if (unmatchedQ.isEmpty()) {
            val combined = l1Answers!!.entries.sortedBy { it.key }
                .joinToString("\n") { (q, a) -> "[$q] $a" }
            val questionSources = l1Answers.entries.associate { it.key to "📋 题库匹配" }
            val source = AnswerSource.EXCEL_MATCH
            Log.d(TAG, "All ${allQ.size} matched by L1, returning")
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, combined, source, emptyList(), questionSources)
            )
            return
        }

        // Build text for ONLY unmatched questions
        val unmatchedText = extractUnmatchedQuestionText(text, l1Keys, unmatchedQ.toSet())
        val userMessage = "以下是考试界面提取的文字中**未匹配**的题目，请根据内容答题：\n\n$unmatchedText"

        // L3: Tavily search
        val enhancement = trySearchEnhancement(config, unmatchedText)
        val effectiveMessage = if (enhancement.found && enhancement.references.isNotEmpty()) {
            val searchRef = enhancement.references.take(3).joinToString("\n") { ref ->
                "- ${ref.title}: ${ref.snippet.take(200)}"
            }
            "$userMessage\n\n---\n以下是网络搜索到的参考资料，请结合你的知识判断使用：\n$searchRef"
        } else userMessage

        // L4: LLM for ONLY the unmatched questions
        callLLMAndCombine(
            config = config,
            effectiveMessage = effectiveMessage,
            requestStartMs = requestStartMs,
            text = text,
            maxTokens = maxTokens,
            l1Answers = l1Answers ?: emptyMap(),
            unmatchedQ = unmatchedQ,
            enhancement = enhancement
        )
    }

    // ── L1: Excel 题库精准匹配 ───────────────────────────

    private suspend fun tryExcelMatchAll(text: String): Map<Int, String>? {
        val excelHits = KnowledgeBaseManager.activeKB?.search(text, topN = 50) ?: emptyList()
        // TEMP DEBUG
        excelHits.forEach { (entry, score) ->
            Log.d(TAG_DEBUG, "KB raw hit: score=${"%.4f".format(score)} q=${entry.question.take(40)} ans=${entry.answer}")
        }
        // END TEMP DEBUG
        val hits = excelHits.filter { (_, score) -> score >= 0.50f }
        if (hits.isEmpty()) return null
        
        val numberedPairs = hits.mapNotNull { (entry, _) ->
            val qNum = findQuestionNumber(text, entry.question) ?: run {
                // TEMP DEBUG
                Log.d(TAG_DEBUG, "findQuestionNumber FAILED for: ${entry.question.take(40)}")
                // END TEMP DEBUG
                return@mapNotNull null
            }
            qNum to normalizeTfAnswer(entry.answer, entry.source)
        }
        
        // Detect conflicts: same question number with different answers
        val conflictQ = mutableSetOf<Int>()
        val answerByQ = mutableMapOf<Int, MutableSet<String>>()
        for ((q, ans) in numberedPairs) {
            val answers = answerByQ.getOrPut(q) { mutableSetOf() }
            answers.add(ans)
            if (answers.size > 1) conflictQ.add(q)
        }
        
        // Build result: non-conflicted questions first
        val numbered = mutableMapOf<Int, String>()
        for ((q, ans) in numberedPairs) {
            if (q in conflictQ) continue
            if (q !in numbered) numbered[q] = ans
        }

        // Resolve conflicts: use options text matching, fall back to score-based
        if (conflictQ.isNotEmpty()) {
            // Extract question blocks from exam text
            val qPattern = Regex("""(\d+)、""")
            val qMatches = qPattern.findAll(text).toList()
            val blocks = if (qMatches.size <= 1) {
                listOf(text)
            } else {
                qMatches.indices.map { i ->
                    val start = qMatches[i].range.first
                    val end = if (i + 1 < qMatches.size) qMatches[i + 1].range.first else text.length
                    text.substring(start, end).trim()
                }
            }
            
            for (qNum in conflictQ.toMutableSet()) {
                val block = blocks.firstOrNull { it.startsWith("$qNum、") } ?: continue
                val capturedOptions = extractCapturedOptions(block)
                
                // Get original hits for this question
                val entriesForQ = hits.mapNotNull { (entry, score) ->
                    val q = findQuestionNumber(text, entry.question) ?: return@mapNotNull null
                    if (q != qNum) return@mapNotNull null
                    entry to score
                }
                
                // Try options-based resolution first
                if (capturedOptions.isNotBlank() && entriesForQ.any { it.first.options.isNotBlank() }) {
                    val withOptions = entriesForQ.filter { it.first.options.isNotBlank() }
                    val normalizedCaptured = normalizeOptions(capturedOptions)
                    val capTri2 = KBEntry.computeTrigrams(normalizedCaptured)
                    val bestEntry = withOptions.maxByOrNull { (entry, _) ->
                        KBEntry.jaccard(capTri2, KBEntry.computeTrigrams(normalizeOptions(entry.options)))
                    }
                    if (bestEntry != null) {
                        val bestSim = KBEntry.jaccard(capTri2, KBEntry.computeTrigrams(normalizeOptions(bestEntry.first.options)))
                        if (bestSim >= 0.50f) {
                            val matchingAnswers = withOptions
                                .filter { (entry, _) ->
                                    KBEntry.jaccard(capTri2, KBEntry.computeTrigrams(normalizeOptions(entry.options))) >= 0.50f
                                }
                                .map { (entry, _) -> normalizeTfAnswer(entry.answer, entry.source) }
                                .distinct()
                            if (matchingAnswers.size == 1) {
                                conflictQ.remove(qNum)
                                numbered[qNum] = matchingAnswers.first()
                                continue
                            }
                        }
                    }
                }
                
                // Fall back to score-based resolution
                val bestEntryByScore = entriesForQ.maxByOrNull { it.second }
                if (bestEntryByScore != null) {
                    val (bestE, bestScore) = bestEntryByScore
                    val bestAns = normalizeTfAnswer(bestE.answer, bestE.source)
                    val ties = entriesForQ.filter { (e, s) ->
                        s == bestScore && normalizeTfAnswer(e.answer, e.source) != bestAns
                    }
                    if (ties.isEmpty()) {
                        conflictQ.remove(qNum)
                        numbered[qNum] = bestAns
                        Log.d(TAG_DEBUG, "score-resolve: Q$qNum score=${"%.4f".format(bestScore)} ans=$bestAns")
                    } else {
                        Log.d(TAG_DEBUG, "score-resolve: Q$qNum tied $bestAns vs ${ties.first().second}, keeping conflict")
                    }
                }
            }
            Log.d(TAG_DEBUG, "resolve: remaining conflicts=$conflictQ")
        }
        
        if (conflictQ.isNotEmpty()) {
            Log.d(TAG, "L1 unresolved conflicts for questions: $conflictQ — will fall through to LLM")
        }
        
        Log.d(TAG, "L1 matched ${numbered.size} questions: ${numbered.keys.sorted()}")
        return if (numbered.isEmpty()) null else numbered
    }

    // ── L2: Wiki 知识库检索 ─────────────────────────────

    private suspend fun tryWikiMatchAll(text: String, skipNumbers: Set<Int>): Map<Int, String> {
        val wikiResult = kbEngine.searchByQuestion(text)
        val combinedPages = (wikiResult.ftsPages + wikiResult.trigramPages).distinctBy { it.id }
        if (combinedPages.isEmpty()) return emptyMap()

        val topScore = combinedPages.maxOfOrNull { page ->
            val pTri = KBEntry.computeTrigrams(page.title + page.summary.take(200))
            val qTri = KBEntry.computeTrigrams(text)
            KBEntry.jaccard(qTri, pTri)
        } ?: 0f

        if (topScore < 0.50f) return emptyMap()

        // Build a per-question match: check which exam questions this wiki page is relevant to
        val answer = kbEngine.getAnswerFromKB(text, combinedPages) ?: ""
        if (answer.isBlank()) return emptyMap()

        // Use the highest-scoring wiki page to answer all unmatched questions
        val unmatchedQ = extractQuestionNumbers(text).map { it.first }.filter { it !in skipNumbers }
        val result = mutableMapOf<Int, String>()
        for (q in unmatchedQ) {
            result[q] = answer
        }
        Log.d(TAG, "L2 matched ${result.size} questions")
        return result
    }

    // ── Context helpers ──────────────────────────────────

    private fun getExcelHints(text: String): List<Pair<KBEntry, Float>> {
        return KnowledgeBaseManager.activeKB?.search(text, topN = 5)
            ?.filter { (_, score) -> score >= 0.40f } ?: emptyList()
    }

    private suspend fun getWikiScore(text: String): Pair<List<WikiPage>, Float> {
        val wikiResult = kbEngine.searchByQuestion(text)
        val combinedPages = (wikiResult.ftsPages + wikiResult.trigramPages).distinctBy { it.id }
        val topScore = combinedPages.maxOfOrNull { page ->
            val pTri = KBEntry.computeTrigrams(page.title + page.summary.take(200))
            val qTri = KBEntry.computeTrigrams(text)
            KBEntry.jaccard(qTri, pTri)
        } ?: 0f
        return combinedPages to topScore
    }

    private fun buildBaseMessage(
        userMessage: String,
        excelHints: List<Pair<KBEntry, Float>>,
        wikiHints: List<WikiPage>
    ): String {
        if (excelHints.isEmpty() && wikiHints.isEmpty()) return userMessage
        val parts = mutableListOf<String>()
        if (excelHints.isNotEmpty()) {
            val excelCtx = excelHints.joinToString("\n") { (e, _) ->
                "题目: ${e.question}\n答案: ${e.answer}"
            }
            parts.add("以下是题库中匹配的题目和答案，请优先参考：\n\n$excelCtx")
        }
        if (wikiHints.isNotEmpty()) {
            val wikiCtx = wikiHints.joinToString("\n\n") { page ->
                "【${page.title}】\n${page.summary}\n${page.content.take(300)}"
            }
            parts.add("以下是知识库中的相关知识点，请参考后作答：\n\n$wikiCtx")
        }
        return parts.joinToString("\n\n") + "\n\n$userMessage"
    }

    // ── Question number helpers ──────────────────────────

    private fun extractQuestionNumbers(text: String): List<Pair<Int, IntRange>> {
        val pattern = Regex("""(\d+)、""")
        return pattern.findAll(text).mapNotNull { match ->
            val num = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val start = match.range.first
            num to (start..start)
        }.toList()
    }

    private fun extractUnmatchedQuestionText(text: String, matchedNumbers: Set<Int>, unmatchedNumbers: Set<Int>): String {
        val questionPattern = Regex("""(\d+)、""")
        val matches = questionPattern.findAll(text).toList()

        val result = StringBuilder()
        for (unmatched in unmatchedNumbers.sorted()) {
            val matchIdx = matches.indexOfFirst { it.groupValues[1].toIntOrNull() == unmatched }
            if (matchIdx < 0) continue
            val start = matches[matchIdx].range.first

            // Find end: start of next question OR end of text
            val end = if (matchIdx + 1 < matches.size) {
                matches[matchIdx + 1].range.first
            } else {
                text.length
            }

            result.append("第${unmatched}题：").append(text.substring(start, end).trim()).append("\n\n")
        }
        return result.toString()
    }

    private fun findQuestionNumber(text: String, kbQuestion: String): Int? {
        val normalizedQuery = text.replace(Regex("（\\s*）"), "（）")
        val normalizedQuestion = kbQuestion.replace(Regex("（\\s*）"), "（）")
        val questionPattern = Regex("""(\d+)、""")

        // 1) Exact substring match (fast path)
        val exactIdx = normalizedQuery.indexOf(normalizedQuestion)
        if (exactIdx >= 0) {
            return questionPattern.findAll(normalizedQuery.substring(0, exactIdx))
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .lastOrNull()
        }

        // 2) Fuzzy match: trigram similarity to find best-matching question block
        val qPattern = Regex("""(\d+)、""")
        val qMatches = qPattern.findAll(normalizedQuery).toList()
        val blocks = if (qMatches.size <= 1) {
            listOf(normalizedQuery)
        } else {
            qMatches.indices.map { i ->
                val start = qMatches[i].range.first
                val end = if (i + 1 < qMatches.size) qMatches[i + 1].range.first else normalizedQuery.length
                normalizedQuery.substring(start, end).trim()
            }
        }
        val kbTri = KBEntry.computeTrigrams(normalizedQuestion)
        var bestBlockIdx = -1
        var bestScore = 0.30f
        for ((i, block) in blocks.withIndex()) {
            val blockTri = KBEntry.computeTrigrams(block)
            val score = KBEntry.jaccard(kbTri, blockTri)
            if (score > bestScore) {
                bestScore = score
                bestBlockIdx = i
            }
        }
        if (bestBlockIdx < 0) return null

        val matchedBlock = blocks[bestBlockIdx]
        val numMatch = questionPattern.find(matchedBlock)
        val qNum = numMatch?.groupValues?.get(1)?.toIntOrNull()
        if (qNum != null) {
            Log.d(TAG, "findQuestionNumber fuzzy match: Q$qNum score=${"%.2f".format(bestScore)}")
        }
        return qNum
    }

    // ── Search → KB processing ───────────────────────────

    private fun parseKBEntries(text: String): List<KBEntry> {
        if (text.isBlank()) return emptyList()
        val entries = mutableListOf<KBEntry>()
        val regex = Regex("""题目[：:]\s*(.+?)\s*答案[：:]\s*(.+?)(?=\n题目|$)""", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(text).forEach { match ->
            val question = match.groupValues[1].trim()
            val answer = match.groupValues[2].trim()
            if (question.isNotBlank() && answer.isNotBlank()) {
                entries.add(KBEntry(question = question, answer = answer, source = "tavily"))
            }
        }
        return entries
    }

    private suspend fun processSearchToKB(
        searchText: String,
        references: List<Reference>,
        config: ConfigSnapshot
    ): List<KBEntry> {
        if (references.isEmpty()) return emptyList()
        val refText = references.take(5).joinToString("\n\n") { ref ->
            "标题：${ref.title}\n内容：${ref.snippet.take(300)}"
        }
        val processPrompt = """
   你是一个知识库构建助手。请分析以下网络搜索结果，结合考试题目，提取出可能的考试题目和答案。

   考试题目：
   $searchText

   搜索结果：
   $refText

   请从搜索结果中提取与考试题目相关的知识点，生成题目-答案对。
   每行一组，格式严格如下：
   题目：[题目内容]
   答案：[答案内容]

   如果没有足够信息，请输出：无相关信息

   注意：直接输出题目-答案对，不要输出任何思考过程或分析内容。不要使用 reasoning。
        """.trimIndent()

        val result = LLMClient().chatSync(
            endpoint = config.apiEndpoint,
            apiKey = config.apiKey,
            model = config.modelName,
            temperature = 0.1f,
            maxTokens = 4096,
            systemPrompt = "你是一个知识库构建助手，从搜索结果中提取考试题目和答案。",
            userMessage = processPrompt
        )
        return when (result) {
            is LLMClient.Result.Success -> parseKBEntries(result.content)
            else -> emptyList()
        }
    }

    // ── L3: Tavily 联网搜索 ─────────────────────────────

    private suspend fun trySearchEnhancement(config: ConfigSnapshot, text: String): SearchEnhancement {
        if (config.tavilyApiKey.isBlank()) return SearchEnhancement(skipped = true)
        ExtractedTextBus.updateSidebarState(
            SidebarState.Loading("正在搜索相关参考资料...", System.currentTimeMillis(), config.maxTokens)
        )
        val client = TavilyClient(config.tavilyApiKey)
        val searchManager = SearchManager(client)
        return searchManager.searchQuestions(text)
    }

    private suspend fun trySearchKBWithLLM(
        enhancement: SearchEnhancement,
        text: String,
        config: ConfigSnapshot
    ): AnswerSource? {
        if (enhancement.references.isEmpty()) return null
        val entries = processSearchToKB(text, enhancement.references, config)
        if (entries.isEmpty()) return null
        Log.d(TAG, "L3 Search KB: LLM generated ${entries.size} entries")
        val qTri = KBEntry.computeTrigrams(text)
        val match = entries.map { entry ->
            entry to KBEntry.jaccard(qTri, KBEntry.computeTrigrams(entry.question))
        }.filter { it.second >= SEARCH_KB_MATCH_THRESHOLD }
            .maxByOrNull { it.second }
        if (match != null) {
            val (entry, score) = match
            Log.d(TAG, "L3 Search KB match: score=${"%.2f".format(score)}")
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, entry.answer, AnswerSource.SEARCH_MATCH)
            )
            return AnswerSource.SEARCH_MATCH
        }
        Log.d(TAG, "L3 Search KB: no match")
        return null
    }

    private fun enhanceWithSearch(enhancement: SearchEnhancement, baseMessage: String): String {
        val searchCtx = enhancement.references.take(5).joinToString("\n") { ref ->
            "【${ref.title}】\n${ref.snippet.take(300)}"
        }
        val searchSummary = if (enhancement.summary.isNotBlank()) {
            "搜索结果摘要：\n${enhancement.summary.take(500)}"
        } else ""
        val combined = listOfNotNull(searchSummary, "参考资料：", searchCtx)
            .joinToString("\n\n")
        return "$combined\n\n$baseMessage"
    }

    // ── L4: LLM 答题 ────────────────────────────────────

    private suspend fun callLLM(
        config: ConfigSnapshot,
        effectiveMessage: String,
        requestStartMs: Long,
        text: String,
        maxTokens: Int,
        source: AnswerSource,
        references: List<Reference>
    ) {
        ExtractedTextBus.updateSidebarState(
            SidebarState.Loading("正在调用 LLM 解答...", requestStartMs, maxTokens)
        )
        try {
            val client = LLMClient()
            val textLen = effectiveMessage.length
            val estTokens = (textLen / 1.5).toInt().coerceAtLeast(50)
            ExtractedTextBus.lastPromptTokens = estTokens
            val accumulated = StringBuilder()
            val estimatedTotalTokens = maxTokens.coerceAtLeast(1)
            var firstChunk = true
            var streamStartMs = 0L
            client.chatStream(
                endpoint = config.apiEndpoint,
                apiKey = config.apiKey,
                model = config.modelName,
                temperature = config.temperature,
                maxTokens = maxTokens,
                systemPrompt = config.systemPrompt,
                userMessage = effectiveMessage
            ).collect { chunk ->
                if (firstChunk) {
                    firstChunk = false
                    streamStartMs = System.currentTimeMillis()
                    ExtractedTextBus.lastTtftMs = streamStartMs - requestStartMs
                }
                accumulated.append(chunk)
                val displayText = if (accumulated.isNotEmpty()) accumulated.toString() else client.reasoningBuffer.toString()
                val roughTokenEstimate = (accumulated.length + client.reasoningBuffer.length) * 2
                val progress = (roughTokenEstimate.toFloat() / estimatedTotalTokens)
                    .coerceIn(0f, 0.95f)
                val elapsed = ((System.currentTimeMillis() - streamStartMs) / 1000).toInt() + 1
                val speed = roughTokenEstimate.toFloat() / elapsed
                if (speed > 0) ExtractedTextBus.lastTokensPerSec = speed
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Streaming(text, displayText, progress, requestStartMs, maxTokens)
                )
            }
            val reasoning = client.reasoningBuffer.toString()
            val finalAnswer = if (accumulated.isNotEmpty()) accumulated.toString()
                else if (reasoning.isNotEmpty()) "【思考过程】\n$reasoning"
                else ""
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, finalAnswer, source, references)
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ExtractedTextBus.updateSidebarState(
                SidebarState.Error("请求异常: ${e.message}")
            )
        }
    }

    // ── L4 + L1 merge: call LLM then combine ──────────────

    private suspend fun callLLMAndCombine(
        config: ConfigSnapshot,
        effectiveMessage: String,
        requestStartMs: Long,
        text: String,
        maxTokens: Int,
        l1Answers: Map<Int, String>,
        unmatchedQ: List<Int>,
        enhancement: SearchEnhancement
    ) {
        ExtractedTextBus.updateSidebarState(
            SidebarState.Loading("正在调用 LLM 解答剩余 ${unmatchedQ.size} 道题...", requestStartMs, maxTokens)
        )
        try {
            val client = LLMClient()
            val textLen = effectiveMessage.length
            val estTokens = (textLen / 1.5).toInt().coerceAtLeast(50)
            ExtractedTextBus.lastPromptTokens = estTokens
            val accumulated = StringBuilder()
            val estimatedTotalTokens = maxTokens.coerceAtLeast(1)
            var firstChunk = true
            var streamStartMs = 0L
            client.chatStream(
                endpoint = config.apiEndpoint,
                apiKey = config.apiKey,
                model = config.modelName,
                temperature = config.temperature,
                maxTokens = maxTokens,
                systemPrompt = config.systemPrompt,
                userMessage = effectiveMessage
            ).collect { chunk ->
                if (firstChunk) {
                    firstChunk = false
                    streamStartMs = System.currentTimeMillis()
                    ExtractedTextBus.lastTtftMs = streamStartMs - requestStartMs
                }
                accumulated.append(chunk)
                val displayText = if (accumulated.isNotEmpty()) accumulated.toString() else client.reasoningBuffer.toString()
                val roughTokenEstimate = (accumulated.length + client.reasoningBuffer.length) * 2
                val progress = (roughTokenEstimate.toFloat() / estimatedTotalTokens).coerceIn(0f, 0.95f)
                val elapsed = ((System.currentTimeMillis() - streamStartMs) / 1000).toInt() + 1
                val speed = roughTokenEstimate.toFloat() / elapsed
                if (speed > 0) ExtractedTextBus.lastTokensPerSec = speed
                // Show streaming with L1 answers prepended
                val l1Text = l1Answers.entries.sortedBy { it.key }
                    .joinToString("\n") { (q, a) -> "[$q] $a" }
                val streamingDisplay = if (l1Text.isNotEmpty()) {
                    "$l1Text\n\n— LLM 答题中 —\n$displayText"
                } else displayText
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Streaming(text, streamingDisplay, progress, requestStartMs, maxTokens)
                )
            }
            val reasoning = client.reasoningBuffer.toString()
            val l4Answer = if (accumulated.isNotEmpty()) accumulated.toString()
                else if (reasoning.isNotEmpty()) "【思考过程】\n$reasoning"
                else ""
            Log.d(TAG, "L4 answer raw length: ${l4Answer.length}, first 200 chars: ${l4Answer.take(200)}")

            // Parse L4 answer to extract per-question answers
            val l4Parsed = parseL4Answer(l4Answer, unmatchedQ)
            Log.d(TAG, "L4 parsed: ${l4Parsed.size} questions: ${l4Parsed.keys.sorted()}")

            // Combine L1 + L4 using formatCombinedAnswer (L1 priority on collision)
            val combined = formatCombinedAnswer(l4Parsed, l1Answers)
            Log.d(TAG, "Combined answer length: ${combined.length}, first 100: ${combined.take(100)}")

            val finalAnswer = combined

            val l1SourceLabel = "📋 题库匹配"
            val l4SourceLabel = "🤖 AI模型"
            val questionSources = mutableMapOf<Int, String>()
            l1Answers.forEach { (q, _) -> questionSources[q] = l1SourceLabel }
            l4Parsed.forEach { (q, _) -> questionSources[q] = l4SourceLabel }
            val source = if (l4Parsed.isEmpty()) AnswerSource.EXCEL_MATCH else AnswerSource.LLM_DIRECT

            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, finalAnswer, source, enhancement.references, questionSources)
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ExtractedTextBus.updateSidebarState(
                SidebarState.Error("请求异常: ${e.message}")
            )
        }
    }

    private fun parseL4Answer(l4Answer: String, expectedNumbers: List<Int>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for (line in l4Answer.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = L4_PATTERN.find(trimmed) ?: continue
            val qNum = match.groupValues[1].toIntOrNull() ?: continue
            val ans = normalizeAnswer(match.groupValues[2].trim())
            if (qNum in expectedNumbers && ans.isNotBlank()) {
                result[qNum] = ans
            }
        }
        return result
    }

    private fun normalizeAnswer(text: String): String {
        val t = text.trim().removePrefix(":").removePrefix("：").removePrefix(".").trim()
        if (Regex("""^(正确|对|yes|✓|√|是|true|T)$""", RegexOption.IGNORE_CASE).matches(t)) return "正确"
        if (Regex("""^(错误|错|no|×|✗|否|false|F)$""", RegexOption.IGNORE_CASE).matches(t)) return "错误"
        val letters = Regex("""[A-Fa-f]""").findAll(t).map { it.value.uppercase() }.toSet()
        if (letters.isNotEmpty()) return letters.sorted().joinToString(" ")
        return t.take(20)
    }

    companion object {
        private const val TAG = "SolvePipeline"
        private const val TAG_DEBUG = "ExamHelperL1"
        const val SEARCH_KB_MATCH_THRESHOLD = 0.70f
        private val L4_PATTERN = Regex("""[\[【]?(\d+)[\]】]?\s*[.、:：)）]?\s*(.+)$""")

        /** Format combined answer map to sorted [N] answer lines. L1 overrides L4 on key collision. */
        fun formatCombinedAnswer(l4Answers: Map<Int, String>, l1Answers: Map<Int, String>): String {
            return (l4Answers + l1Answers).entries.sortedBy { it.key }
                .joinToString("\n") { (q, a) -> "[$q] $a" }
        }

        /** Convert KB letter answer to text for true-false questions based on source mapping. */
        fun normalizeTfAnswer(answer: String, source: String): String {
            val tfRe = Regex("""([A-F])-(正确|错误|对|错)\|([A-F])-(正确|错误|对|错)""")
            val m = tfRe.find(source) ?: return answer
            val map = mapOf(m.groupValues[1] to m.groupValues[2], m.groupValues[3] to m.groupValues[4])
            return map[answer] ?: answer
        }

        /** Extract option lines (e.g. "A.更改 B.屏蔽 C.清除 D.确认") from a captured question block. */
        fun extractCapturedOptions(block: String): String {
            val lines = block.lines()
            val optionStart = lines.indexOfFirst { Regex("""^[A-Z]\s*[.、:：)）]""").containsMatchIn(it) }
            if (optionStart < 0) return ""
            return lines.drop(optionStart).joinToString(" ").trim()
        }

        /** Normalize option format for comparison: replace any letter-separator with a common one. */
        fun normalizeOptions(s: String): String {
            // Step 1: normalize inter-option separators (|, etc.) to space
            val spaced = s.replace(Regex("""[|]+"""), " ")
            // Step 2: normalize letter-separator pattern to "A. " format
            return spaced.replace(Regex("""(?<=^|[ ])[A-Z]\s*[.、:：)）\-]""")) {
                "${it.value.first()}. "
            }
        }
    }
}
