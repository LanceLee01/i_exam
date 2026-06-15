# Fix: Tavily 搜索结果 → LLM 结构化 → KB 匹配

## TL;DR

> **Quick Summary**: Tavily 搜索结果先用 LLM (`chatSync`) 处理成结构化 KBEntry（题目+答案对），再走 L1 Jaccard 匹配（阈值 0.70）。匹配成功直接返回，失败则走原始 LLM 答题（不注入搜索上下文）。搜索引用仍在侧边栏展示。
>
> **Deliverables**:
> - `SolvePipeline.kt` — 新增 `processSearchToKB()` + `trySearchKBWithLLM()` 方法，修改 `solve()` 流程
>
> **Estimated Effort**: Short (~1 小时)
> **Parallel Execution**: NO (single file change)

---

## Context

### Problem
Tavily 搜索结果直接注入 LLM 提示词会干扰 LLM 判断，导致答案不对题。

### Solution
先让 LLM 把搜索结果处理成结构化 KBEntry（题目→答案对），然后用 L1 Jaccard 匹配。因为 KBEntry 是 Q&A 格式，与考试题目格式一致，匹配准确率高。匹配成功则直接使用，降低对 LLM 实时答题的依赖。

### Flow Comparison
```
Current (broken):
  搜索 → 注入LLM提示词最前面 → LLM被干扰 → 答案错

方案B (new):
  搜索 → LLM处理成结构化Q&A → 创建临时KBEntry → L1 Jaccard匹配(≥0.70)
    → 匹配成功：直接返回 ✅
    → 匹配失败：LLM原始答题（无搜索注入）
```

### Why This Works
| 步骤 | 说明 |
|------|------|
| LLM 处理搜索结果 | 将杂乱的网页片段提炼为「题目→答案」对，格式与 KB 一致 |
| L1 Jaccard 匹配 | 结构化 Q&A 与考试题目的 Trigram 重叠度高，0.70 阈值可达 |
| 匹配失败走原始 LLM | 不注入搜索上下文，LLM 基于自身知识和 KB 回答 |
| chatSync (非流式) | 处理步骤不需要流式显示，更快更省 |

---

## Work Objectives

### Core Objective
Tavily 搜索结果 → LLM 结构化 → KBEntry → L1 Jaccard 匹配 → 匹配成功出答案 / 失败则原始 LLM

### Must Have
- [ ] 新增 `processSearchToKB()` — 调用 LLM chatSync 处理搜索结果，返回 List<KBEntry>
- [ ] 新增 `trySearchKBWithLLM()` — 对处理后的 KBEntry 做 L1 Jaccard 匹配（阈值 0.70）
- [ ] 匹配成功 → `Done(text, answer, SEARCH_MATCH)`
- [ ] 匹配失败 → 走原始 LLM 答题（不注入搜索上下文，references 只用于展示）
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `./gradlew test` → BUILD SUCCESSFUL

### Must NOT Have
- ❌ 不修改 TavilyClient.kt、SearchManager.kt、ExtractedTextBus.kt
- ❌ 不修改 SidebarPanel/SidebarStateRenderer 展示逻辑（引用链接依然显示）
- ❌ 不修改 AppConfig.kt（复用现有 LLM 配置）
- ❌ 不修改 L1/L2 的阈值（0.70/0.50）

---

## TODOs

- [x] 1. **SolvePipeline.kt: 新增 LLM 处理 + KB 匹配逻辑**

  **What to do**:

  **Part A — 新增 `processSearchToKB()` 方法**
  
  调用 LLM chatSync（非流式）将搜索结果处理为结构化 KBEntry：

  ```kotlin
  /**
   * 调用 LLM 将搜索结果处理为结构化 KBEntry（题目→答案对）。
   * 使用 chatSync（非流式，更快），返回 List<KBEntry>。
   */
  private suspend fun processSearchToKB(
      searchText: String,  // Tavily 的查询文本（考试题目）
      references: List<Reference>,
      config: ConfigSnapshot
    ): List<KBEntry> {
      // 构建处理 prompt
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
      """.trimIndent()
      
      val result = LLMClient().chatSync(
          endpoint = config.apiEndpoint,
          apiKey = config.apiKey,
          model = config.modelName,
          temperature = 0.3f,
          maxTokens = 1024,
          systemPrompt = "你是一个知识库构建助手，从搜索结果中提取考试题目和答案。",
          userMessage = processPrompt
      )
      
      return when (result) {
          is LLMClient.Result.Success -> parseKBEntries(result.content)
          else -> emptyList()
      }
  }

  /**
   * 解析 LLM 输出为 KBEntry 列表
   * 格式：题目：xxx\n答案：xxx
   */
  private fun parseKBEntries(text: String): List<KBEntry> {
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
  ```

  **Part B — 新增 `trySearchKBWithLLM()` 方法**
  
  对处理后的 KBEntry 运行 L1 Jaccard 匹配：

  ```kotlin
  companion object {
      private const val SEARCH_KB_MATCH_THRESHOLD = 0.70f  // 与 L1 相同阈值
  }

  /**
   * 对 LLM 处理后的 KBEntry 做 L1 Jaccard 匹配。
   * 匹配成功则更新 SidebarState 并返回 AnswerSource，失败返回 null。
   */
  private suspend fun trySearchKBWithLLM(
      enhancement: SearchEnhancement,
      text: String,
      config: ConfigSnapshot
  ): AnswerSource? {
      if (enhancement.references.isEmpty()) return null
      
      // LLM 处理搜索结果 → KBEntry
      val entries = processSearchToKB(text, enhancement.references, config)
      if (entries.isEmpty()) return null
      
      Log.d(TAG, "L3 Search KB: LLM generated ${entries.size} entries")
      
      // L1 Jaccard 匹配
      val qTri = KBEntry.computeTrigrams(text)
      val match = entries.map { entry ->
          entry to KBEntry.jaccard(qTri, entry.trigrams)
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
  ```

  **Part C — 修改 `solve()` 流程**
  
  将 L3/L4 部分改为：

  ```kotlin
  // L3: Tavily 搜索 → LLM 处理为 KBEntry → 匹配
  val enhancement = trySearchEnhancement(config, text)
  if (trySearchKBWithLLM(enhancement, text, config) != null) return

  // L4: LLM 答题（不注入搜索上下文，只传引用供展示）
  callLLM(
      config = config,
      effectiveMessage = baseMessage,
      requestStartMs = requestStartMs,
      text = text,
      maxTokens = maxTokens,
      source = baseSource,
      references = enhancement.references  // 只用于侧边栏展示
  )
  ```

  **Must NOT do**:
  - ❌ 不修改 L1/L2 逻辑
  - ❌ 不从 `solve()` 调用 `enhanceWithSearch()`
  - ❌ 不修改其他文件

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 核心流程修改，需要精确处理 LLM 二次调用 + KB 匹配

  **References**:
  - `SolvePipeline.kt:22-60` — 当前 solve() 流程
  - `LLMClient.kt:97-143` — `chatSync()` 非流式调用（参考模式）
  - `KnowledgeBaseManager.kt:27-47` — KBEntry 的 computeTrigrams + jaccard
  - `KBEngine.kt:232-261` — `buildWikiPrompt()` 参考现有 LLM 处理文档的 prompt 设计
  - `SolvePipeline.kt:137-157` — 当前的 trySearchEnhancement()

  **Acceptance Criteria**:
  - [ ] `processSearchToKB()` 存在，使用 `chatSync` 调用 LLM
  - [ ] `parseKBEntries()` 存在，解析「题目：xxx 答案：xxx」格式
  - [ ] `trySearchKBWithLLM()` 存在，阈值 0.70
  - [ ] 匹配成功 → `Done(SEARCH_MATCH)`；失败 → 走 LLM 不注入
  - [ ] `callLLM()` 仍传入 `enhancement.references`
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL

  **QA Scenarios**:
  ```
  Scenario: New methods exist
    Tool: Bash (grep)
    Steps:
      1. grep "fun processSearchToKB" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
      2. grep "fun trySearchKBWithLLM" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
      3. grep "fun parseKBEntries" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
      4. grep "SEARCH_KB_MATCH_THRESHOLD" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
    Expected Result: All 4 patterns found
    Evidence: .omo/evidence/fix-kb-methods.txt

  Scenario: Build + tests pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug && ./gradlew test
    Expected Result: Both exit 0
    Evidence: .omo/evidence/fix-kb-build-test.txt

  Scenario: Search references still passed for display
    Tool: Bash (grep)
    Steps:
      1. grep "enhancement.references" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
    Expected Result: references still passed to callLLM
    Evidence: .omo/evidence/fix-kb-refs.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出
  - [ ] build + test 输出

  **Commit**: YES
  - Message: `fix: process Tavily search results via LLM into KBEntry, then L1 match (0.70)`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`

---

## Success Criteria

### Verification Commands
```bash
./gradlew assembleDebug  # Expected: BUILD SUCCESSFUL
./gradlew test            # Expected: BUILD SUCCESSFUL, all tests pass
```

### Final Checklist
- [ ] Search results → LLM processed into structured KBEntry
- [ ] L1 Jaccard matching (0.70) on processed entries
- [ ] On match: answer returned via SEARCH_MATCH
- [ ] On no match: LLM runs with clean prompt (no search injection)
- [ ] Reference links displayed in sidebar
- [ ] Build + tests pass
