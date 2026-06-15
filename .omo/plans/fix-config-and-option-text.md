# Fix: Default Config + Option Text Display

## TODOs

- [ ] 1. **AppConfig.kt: 修改默认 model 和 max_tokens**

  **What to do**:
  - `DEFAULT_MODEL` → `"deepseek-v4-flash"`
  - `DEFAULT_MAX_TOKENS` → `10240`

  **Verification**: `./gradlew assembleDebug` → BUILD SUCCESSFUL

- [ ] 2. **修复选项文字显示问题**

  **根因分析**：

  `parseOptionMap()` 用 Regex `^([A-F])[.、．\s]\s*(.+)` (MULTILINE) 从考试文本中提取选项字母→文字映射。

  问题是：当屏幕上有**多个题目**时，每道题都有自己的 A/B/C/D 选项，但 `parseOptionMap` 只记录**每个字母的第一次出现**。比如：
  ```
  题1: A.36V  B.220V
  题2: A.升压  B.降压
  ```
  映射结果是 `A→36V, B→220V`（题1的覆盖了题2的）。于是题2答案 `[2] B` 显示为 `B.220V`（错的）。

  **另一个可能**：如果选项是**横排显示**（`A.36V B.220V C.110V` 在同一行），MULTILINE 的 `^` 只匹配行首，B/C/D 会完全匹配不到。

  **修复方案**：禁用选项文字追加，直接显示 LLM 原始答案。

  改动：在 `SidebarStateRenderer.kt` 中，将 `appendOptionText(line, optionMap)` 改为直接使用 `line`，不追加选项文字。

  ```kotlin
  // 改前
  text = appendOptionText(line, optionMap),
  // 改后（跳过选项文字追加，直接显示 LLM 原始答案）
  text = line,
  ```

  **理由**：
  - 选项文字追加只是装饰性功能（让答案看起来更 readable）
  - 对答题正确性无影响
  - LLM 本身的答案 `[1] A` 已经正确
  - 去掉后消除显示错误的可能性

  **Verification**: `./gradlew assembleDebug` → BUILD SUCCESSFUL

- [ ] 3. **重新编译 + 安装**

  `./gradlew assembleDebug && adb install -r app-debug.apk`
