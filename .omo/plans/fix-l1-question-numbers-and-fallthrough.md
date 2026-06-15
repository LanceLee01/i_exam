# Fix: L1 显示题号 + 未匹配题继续走 LLM

## 问题
1. L1 匹配后只显示答案字母（如 "C"），没有题号，用户不知道对应哪道题
2. L1 一旦有任一匹配就 early-return，剩下的题不会走 LLM

## 修复思路

1. **题号提取**：从屏幕提取文字中找出 `1、...`、`2、...` 这样的题号模式，根据 KB 题目文本在屏幕中的位置确定对应题号
2. **不 early-return**：L1 只标记已匹配的题号，剩下未匹配题由 L4 填空

## 改动

### 1. 新增 `extractQuestionNumbers(text)` 工具函数

```kotlin
private fun extractQuestionNumbers(text: String): List<Pair<Int, IntRange>> {
    val pattern = Regex("""(\d+)、""")
    return pattern.findAll(text).mapNotNull { match ->
        val num = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val start = match.range.first
        val end = match.range.last
        num to (start..end)
    }.toList()
}
```

### 2. 新增 `findQuestionNumber(text, kbQuestion)` 

```kotlin
private fun findQuestionNumber(text: String, kbQuestion: String): Int? {
    val normalizedQuery = text.replace(Regex("（\\s*）"), "（）")
    val normalizedQuestion = kbQuestion.replace(Regex("（\\s*）"), "（）")
    val idx = normalizedQuery.indexOf(normalizedQuestion)
    if (idx < 0) return null
    val questionPattern = Regex("""(\d+)、""")
    return questionPattern.findAll(normalizedQuery.substring(0, idx))
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .lastOrNull()
}
```

### 3. 重构 `solve()` 流程

```kotlin
suspend fun solve(text: String) {
    val requestStartMs = System.currentTimeMillis()
    val config = ExamApplication.instance.appConfig.getSnapshot()
    val maxTokens = config.maxTokens
    val userMessage = "以下是考试界面提取的文字，请根据内容答题：\n\n$text"

    // L1: 找所有匹配（不再 early-return）
    val l1Answers = tryExcelMatchAll(text)  // Map<Int, String>?  null if empty
    val matchedQ = l1Answers?.keys?.toSet() ?: emptySet()
    
    // L2: 找匹配但跳过 L1 已答的
    val l2Answers = tryWikiMatchAll(text, matchedQ)  // Map<Int, String>
    val matchedQ2 = (l1Answers?.keys ?: emptySet()) + l2Answers.keys

    // 找出所有题号
    val allQNumbers = extractQuestionNumbers(text).map { it.first }.toSet()
    val unmatchedQ = allQNumbers - matchedQ2

    if (unmatchedQ.isEmpty()) {
        // 全匹配完了，直接出答案
        val finalAnswer = (l1Answers!! + l2Answers).entries.sortedBy { it.key }
            .joinToString("\n") { (q, a) -> "[$q] $a" }
        val source = if (l1Answers.isNotEmpty()) AnswerSource.EXCEL_MATCH else AnswerSource.KB_MATCH
        ExtractedTextBus.updateSidebarState(SidebarState.Done(text, finalAnswer, source))
        return
    }

    // L4: 用 LLM 答未匹配的题
    callLLM(
        config = config,
        effectiveMessage = baseMessage,
        ...
        text = text,
        maxTokens = maxTokens,
        ...
    )
}
```

### 4. `tryExcelMatchAll` 替代 `tryExcelMatch`

```kotlin
private suspend fun tryExcelMatchAll(text: String): Map<Int, String>? {
    val excelHits = KnowledgeBaseManager.activeKB?.search(text, topN = 50) ?: emptyList()
    val hits = excelHits.filter { (_, score) -> score >= 0.70f }
    if (hits.isEmpty()) return null
    
    val numbered = hits.mapNotNull { (entry, _) ->
        val qNum = findQuestionNumber(text, entry.question) ?: return@mapNotNull null
        qNum to entry.answer
    }
    Log.d(TAG, "L1 matched ${numbered.size} questions: ${numbered.keys.sorted()}")
    return if (numbered.isEmpty()) null else numbered.toMap()
}
```

### 5. `tryWikiMatchAll` 替代 `tryWikiMatch`

类似改动，返回 `Map<Int, String>` 而不是 `AnswerSource?`。
