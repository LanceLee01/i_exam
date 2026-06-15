# Fix: Excel KB 精确匹配

## 问题
L1 匹配把整个屏幕文字（1212字符，10道题）作为 query 去 Jaccard 匹配单条 KB 题目（80字符），即使题目100%在屏幕里，Jaccard 也只有 0.06 左右，永远达不到 0.70 阈值。

## 修复

在 `KnowledgeBase.search()` 中，先做**子串包含检查**：

```kotlin
fun search(query: String, topN: Int = 5): List<Pair<KBEntry, Float>> {
    if (entries.isEmpty()) return emptyList()
    
    // 先检查是否有题目文本直接包含在 query 中（精确匹配）
    val exactMatches = entries.filter { query.contains(it.question) }
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

改动点：`KnowledgeBaseManager.kt:116-126` 的 `search()` 方法。

编译验证 + 安装。
