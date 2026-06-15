# Fix: L1 返回所有匹配的答案

## 问题
`tryExcelMatch` 只取了第一个匹配结果就返回，导致多题考试只答了1题。

## 修复

1. **`search()` 的 `topN` 从 5 改为 50**（`KnowledgeBaseManager.kt`）
2. **`tryExcelMatch` 收集所有匹配答案**（`SolvePipeline.kt`）

### KnowledgeBaseManager.kt
```kotlin
// search() 中
val exactMatches = entries.filter { ... }
if (exactMatches.isNotEmpty()) {
    return exactMatches.map { it to 1.0f }.take(topN)
    // topN 默认 5 → 改为 50
}
```

改 `search()` 默认参数：`fun search(query: String, topN: Int = 50)`

### SolvePipeline.kt
```kotlin
private suspend fun tryExcelMatch(text: String, config: ConfigSnapshot): AnswerSource? {
    val excelHits = KnowledgeBaseManager.activeKB?.search(text, topN = 50) ?: emptyList()
    val hits = excelHits.filter { (_, score) -> score >= 0.70f }
    if (hits.isEmpty()) return null
    val answerText = hits.joinToString("\n") { (entry, _) -> entry.answer }
    Log.d(TAG, "L1 Excel match: ${hits.size} questions matched")
    ExtractedTextBus.updateSidebarState(
        SidebarState.Done(text, answerText, AnswerSource.EXCEL_MATCH)
    )
    return AnswerSource.EXCEL_MATCH
}
```
