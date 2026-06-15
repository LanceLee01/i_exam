# Fix: 顶端来源按行显示（题库匹配 + AI 模型）

## 改动

### 1. `ExtractedTextBus.kt` — Done 加 questionSources 字段

```kotlin
data class Done(
    val text: String,
    val answer: String,
    val source: AnswerSource = AnswerSource.LLM_DIRECT,
    val references: List<Reference> = emptyList(),
    val questionSources: Map<Int, String> = emptyMap()  // 每题来源标签
) : SidebarState()
```

### 2. `SolvePipeline.kt` — `callLLMAndCombine` 填充 questionSources

```kotlin
// 合并各阶段答案为 questionSources map
val l1SourceLabel = "📋 题库匹配"
val l4SourceLabel = "🤖 AI模型"
val questionSources = mutableMapOf<Int, String>()
l1Answers.forEach { (q, _) -> questionSources[q] = l1SourceLabel }
l4Parsed.forEach { (q, _) -> questionSources[q] = l4SourceLabel }

ExtractedTextBus.updateSidebarState(
    SidebarState.Done(text, finalAnswer, source, references, questionSources)
)
```

全 L1 匹配时也填充：
```kotlin
if (unmatchedQ.isEmpty()) {
    val questionSources = l1Answers!!.entries.associate { it.key to "📋 题库匹配" }
    ExtractedTextBus.updateSidebarState(
        SidebarState.Done(text, combined, source, emptyList(), questionSources)
    )
    return
}
```

### 3. `SidebarStateRenderer.kt` — 渲染多行来源

改前：
```kotlin
text = "来源: ${s.source.label}",
```

改后：
```kotlin
if (s.questionSources.isNotEmpty()) {
    val l1 = s.questionSources.filterValues { it.contains("题库") }.keys.sorted()
    val l4 = s.questionSources.filterValues { it.contains("AI") || it.contains("LLM") }.keys.sorted()
    Column {
        if (l1.isNotEmpty()) {
            Text("📋 题库匹配: ${l1.joinToString(", ")}", ...)
        }
        if (l4.isNotEmpty()) {
            Text("🤖 AI模型: ${l4.joinToString(", ")}", ...)
        }
    }
} else {
    Text("来源: ${s.source.label}", ...)
}
```
