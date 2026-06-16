# Fix: findQuestionNumber 增加 trigram 模糊匹配

## Problem
`findQuestionNumber()` 要求 KB 题目文字是考试原文的精确子串。一个字不同（如"不应"vs"不得"）就返回 null，导致第 6 题即便 trigram 匹配度 95%+ 也拿不到 L1 答案。

## Fix
精确子串找不到时，用 trigram Jaccard 找到最匹配的题目块，取该块的题号。

## Change in `SolvePipeline.kt`

```kotlin
private fun findQuestionNumber(text: String, kbQuestion: String): Int? {
    val normalizedQuery = text.replace(Regex("（\\s*）"), "（）")
    val normalizedQuestion = kbQuestion.replace(Regex("（\\s*）"), "（）")
    val questionPattern = Regex("""(\d+)、""")
    
    // 1) 精确子串匹配（快速路径）
    val exactIdx = normalizedQuery.indexOf(normalizedQuestion)
    if (exactIdx >= 0) {
        return questionPattern.findAll(normalizedQuery.substring(0, exactIdx))
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .lastOrNull()
    }
    
    // 2) 模糊匹配：trigram 相似度找到最接近的题目块
    val blocks = extractQuestionBlocks(normalizedQuery)
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
```

## Verification
- 第 6 题："不应" vs "不得"，trigram 相似度 > 0.95 → 匹配到题号 6 → 拿到答案 AC
- 第 3 题重复条目：冲突检测仍生效，继续走 LLM
- 其他精确匹配的题目：走快速路径，行为不变
