# Fix: 题库重复匹配导致错误答案

## TL;DR
**Problem**: KB 中有题目文字完全相同的两道题（如一个单选、一个多选），`tryExcelMatchAll()` 中 `.toMap()` 无声覆盖，导致第 3 题拿到错误答案。

**Fix**: 在 `tryExcelMatchAll()` 中检测冲突——同一题号对应多条不同 KB 条目时，跳过该题，交给 LLM 处理。

---

## Context

### Current Code (`SolvePipeline.kt:78-90`)
```kotlin
val numbered = hits.mapNotNull { (entry, _) ->
    val qNum = findQuestionNumber(text, entry.question) ?: return@mapNotNull null
    qNum to normalizeTfAnswer(entry.answer, entry.source)
}.toMap() // ← 冲突覆盖
```

### Fix
```kotlin
// 先收集所有映射
val numberedPairs = hits.mapNotNull { (entry, _) ->
    val qNum = findQuestionNumber(text, entry.question) ?: return@mapNotNull null
    qNum to normalizeTfAnswer(entry.answer, entry.source)
}

// 检测冲突：重复题号
val conflictQ = mutableSetOf<Int>()
val seenQ = mutableSetOf<Int>()
for ((q, _) in numberedPairs) {
    if (q in seenQ) conflictQ.add(q)
    seenQ.add(q)
}

// 过滤掉冲突
val numbered = numberedPairs
    .filter { (q, _) -> q !in conflictQ }
    .distinctBy { it.first }
    .toMap()
```

Conflict questions fall through → `unmatchedQ` includes them → LLM handles them (with full context of 单选/多选).

---

## TODOs

- [ ] 1. Fix `tryExcelMatchAll()` in SolvePipeline.kt

  **What to do**:
  - Modify `SolvePipeline.kt:78-90`
  - Add conflict detection before `.toMap()`
  - Log conflicts: `"L1 conflict detected for questions: $conflictQ"`
  
  **File**: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`

  **Must NOT do**:
  - Don't change any other files
  - Don't modify KB storage or search logic
  
  **Commit**: YES
  - `fix: skip L1 matching when duplicate KB entries conflict`
