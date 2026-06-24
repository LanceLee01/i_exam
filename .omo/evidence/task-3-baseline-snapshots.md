# Baseline Snapshots (pre-modification)

## SidebarStateRenderer.kt (total: 229 lines)
### Lines 100-205
```kotlin
        is SidebarState.Done -> {
            Log.d("SidebarPanel", "Done state rendered, answer length=${s.answer.length}")
            onDoneState(s.answer, s.text)

            Spacer(Modifier.height(12.dp))
            SectionHeader("答案")
            if (s.questionSources.isNotEmpty()) {
                val l1Questions = s.questionSources.filterValues { it.contains("题库") }.keys.sorted()
                val l4Questions = s.questionSources.filterValues { it.contains("AI") || it.contains("LLM") }.keys.sorted()
                val others = s.questionSources.filterValues { !it.contains("题库") && !it.contains("AI") && !it.contains("LLM") }
                Column {
                    if (l1Questions.isNotEmpty()) {
                        Text(
                            text = "📋 题库匹配: ${l1Questions.joinToString(", ")}",
                            color = Color(0xFF22C55E).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (l4Questions.isNotEmpty()) {
                        Text(
                            text = "🤖 AI模型: ${l4Questions.joinToString(", ")}",
                            color = Color(0xFF3B82F6).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    others.forEach { (q, label) ->
                        Text(
                            text = "$label: $q",
                            color = Color(0xFF22C55E).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "来源: ${s.source.label}",
                    color = Color(0xFF22C55E).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            // 引用链接展示（如果有）
            if (s.references.isNotEmpty()) {
                val llmQuestions = s.questionSources
                    .filterValues { it.contains("AI") || it.contains("LLM") }
                    .keys
                    .sorted()
                
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    if (llmQuestions.isNotEmpty()) "🔍 参考资料（题 ${llmQuestions.joinToString(", ")}）"
                    else "🔍 参考资料"
                )
                Column {
                    s.references.take(5).forEachIndexed { index, ref ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "[${index + 1}] ",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = ref.title,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = ref.url,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val lines = s.answer.lines()
            lines.forEach { line ->
                val isAnswerLine = line.contains("✓") ||
                    Regex("""^\s*[\[【]?\d+[\]】]?\s*[A-Da-d]\b""").containsMatchIn(line)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = if (isAnswerLine) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 2.dp),
                    lineHeight = 22.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            ReworkButton(onClick = { onRework(s.text) })
            Spacer(Modifier.height(8.dp))
            SaveToKBButton(onClick = { onSaveToKB(s.text, s.answer) })
        }

        is SidebarState.Streaming -> {
            Log.d("SidebarPanel", "Streaming state, partialAnswer length=${s.partialAnswer.length}")
```

## ExtractedTextBus.kt
### Lines 26-40
```kotlin
    sealed class SidebarState {
        data object Idle : SidebarState()
        data class Loading(val message: String, val startTimeMs: Long = 0L, val maxTokens: Int = 2048) : SidebarState()
        data class Preview(val text: String) : SidebarState()
        data class Streaming(val text: String, val partialAnswer: String, val progress: Float, val startTimeMs: Long, val maxTokens: Int = 2048) : SidebarState()
        data class Answering(val text: String) : SidebarState()
        data class Done(val text: String, val answer: String, val source: AnswerSource = AnswerSource.LLM_DIRECT, val references: List<Reference> = emptyList(), val questionSources: Map<Int, String> = emptyMap()) : SidebarState()
        data class Error(val message: String) : SidebarState()
    }

    enum class AnswerSource(val label: String) {
        EXCEL_MATCH("📋 题库匹配"),
        KB_MATCH("📖 知识库匹配"),
        KB_INFER("📖 知识库推断"),
        SEARCH_MATCH("🔍 网络搜索"),
```

## TavilyClient.kt
### Lines 75-94
```kotlin
    val source: String
)

data class Reference(
    val title: String,
    val url: String,
    val snippet: String
)

data class TavilyResponse(
    val answer: String?,
    val results: List<TavilyResult>?
)

data class TavilyResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double
)

```

## Theme.kt
### Lines 14-18
```kotlin
val TextCorrect = Color(0xFF22C55E)
val TextError = Color(0xFFEF4444)
val TextSecondary = Color(0xFF9CA3AF)
val EdgeWhite = Color(0x40FFFFFF)
```
