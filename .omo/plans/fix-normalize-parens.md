# Fix: 括号空格规范化

## 问题
KB 条目的填空括号 `（    ）`（4空格）与屏幕提取的 `（ ）`（1空格）不一致，导致 `contains()` 精确匹配失败。

## 修复
在 `search()` 方法中，比较前将括号内空格规范化：

```kotlin
fun search(query: String, topN: Int = 5): List<Pair<KBEntry, Float>> {
    if (entries.isEmpty()) return emptyList()
    
    // 规范化括号内空格，兼容不同数量的空格
    val normalizedQuery = query.replace(Regex("（\\s*）"), "（）")
    
    val exactMatches = entries.filter {
        val normalizedQuestion = it.question.replace(Regex("（\\s*）"), "（）")
        normalizedQuery.contains(normalizedQuestion)
    }
    if (exactMatches.isNotEmpty()) {
        return exactMatches.map { it to 1.0f }.take(topN)
    }
    
    // 无精确匹配，回退到 trigram Jaccard
    val qTrigrams = KBEntry.computeTrigrams(query)
    return entries.map { entry ->
        entry to KBEntry.jaccard(qTrigrams, entry.trigrams)
    }
    .filter { it.second > 0.15f }
    .sortedByDescending { it.second }
    .take(topN)
}
```
