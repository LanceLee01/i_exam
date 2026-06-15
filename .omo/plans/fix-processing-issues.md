# Fix: processSearchToKB 空结果 + FTS 搜索报错

## 问题1: processSearchToKB 返回空列表

**根因**：`deepseek-v4-flash` 模型的输出格式问题。

LLM 返回 `content=""` 但 `reasoning_content` 有内容（模型的思考过程）。`chatSync` 虽然有 fallback 逻辑取 `reasoningContent`，但思考内容不包含 `题目：xxx 答案：xxx` 的结构化格式，所以 `parseKBEntries` 解析结果为空 → `trySearchKBWithLLM` 返回 null → 方案 B 跳过。

**修复**：
1. Prompt 增加「不要输出思考过程，直接输出题目-答案对」
2. `processSearchToKB` 同时检查 `content` 和 `reasoningContent`
3. Temperature 降到 0.1 让输出更确定

## 问题2: FTS 搜索报错

**根因**：`buildFtsQuery` 对长中文文本（10道题）生成数十个 OR 条件，SQLite FTS4 的表达式树最大深度为 12。

**修复**：限制 FTS 查询的 term 数量最多 10 个。

---

## TODOs

- [ ] 1. **修复 processSearchToKB**

  **SolvePipeline.kt** 修改 2 处：
  
  1a. 修改 prompt，增加禁止思考的指令：
  ```
  你是一个知识库构建助手。请分析以下网络搜索结果，结合考试题目，提取出可能的考试题目和答案。
  注意：直接输出题目-答案对，不要输出任何思考过程或分析内容。
  ```
  并添加 `"不要使用 reasoning，不要输出思考过程"`
  
  1b. `processSearchToKB` 中 temperature 从 0.3 改为 0.1

- [ ] 2. **修复 FTS 查询超长**

  **KBEngine.kt** `buildFtsQuery()` 方法末尾增加 `.take(10)` 限制 OR 条件数量：
  ```kotlin
  return cleaned.split(" ")
      .filter { it.length >= 2 }
      .take(10)
      .joinToString(" OR ") { "$it*" }
  ```

- [ ] 3. **重新编译 + 安装**
