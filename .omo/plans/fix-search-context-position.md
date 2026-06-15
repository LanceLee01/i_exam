# Fix: 搜索结果放在题目后面作为辅助参考

## 改动

`SolvePipeline.kt` 的 `solve()` 方法：

1. 移除 `trySearchKBWithLLM()` 调用（方案 B 不适用此模型）
2. Tavily 搜索结果作为辅助参考，**追加到题目文本后面**
3. 保留 `trySearchEnhancement()` 和 `enhancement.references`（侧边栏展示）
4. `trySearchKBWithLLM()` / `processSearchToKB()` / `parseKBEntries()` 方法保留但不再从 `solve()` 调用

## TODOs

- [ ] 1. **修改 solve() 流程**

  **SolvePipeline.kt** 改动：

  **改前**:
  ```kotlin
  // L3: Tavily 搜索 → LLM 处理为 KBEntry → 匹配
  val enhancement = trySearchEnhancement(config, text)
  if (trySearchKBWithLLM(enhancement, text, config) != null) return

  // L4: LLM 答题（不注入搜索上下文，只传引用供展示）
  callLLM(
      config = config,
      effectiveMessage = baseMessage,
      ...
      references = enhancement.references
  )
  ```

  **改后**:
  ```kotlin
  // L3: Tavily 搜索（结果作为辅助参考附加到提示词末尾）
  val enhancement = trySearchEnhancement(config, text)
  val effectiveMessage = if (enhancement.found && enhancement.references.isNotEmpty()) {
      val searchRef = enhancement.references.take(3).joinToString("\n") { ref ->
          "- ${ref.title}: ${ref.snippet.take(200)}"
      }
      "$baseMessage\n\n---\n以下是网络搜索到的参考资料，请结合你的知识判断使用：\n$searchRef"
  } else baseMessage

  // L4: LLM 答题
  callLLM(
      config = config,
      effectiveMessage = effectiveMessage,
      ...
      references = enhancement.references
  )
  ```

  编译验证 + 安装。
