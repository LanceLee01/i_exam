# 修复混合匹配场景自动填入答案格式

## TL;DR

> **Quick Summary**: 修复 `callLLMAndCombine()` 中 `finalAnswer` 的输出格式——当前混合 L1+L4 匹配场景使用展示格式（"📋 题库匹配："标题 + LLM 原始输出），导致 `parseAnswerPairs` 无法解析，自动填入完全失效。改为统一的 `[N] answer` 格式，展示和填入共用同一份干净数据。
>
> **Deliverables**:
> - 修复 `SolvePipeline.kt` `callLLMAndCombine()` 3 个分支的 answer 输出格式
> - 新增 `formatCombinedAnswer()` 纯函数（L1 优先于 L4）
> - TDD 测试：格式函数测试 + 集成测试 + parseAnswerPairs 解析验证
>
> **Estimated Effort**: Quick (2-3 个文件，约 100 行变更)
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: 测试用例 → 实现 → 验证

---

## Context

### Original Request
> "答案要按照题号依次排列。要适用于自动填入功能。"

用户发现混合匹配场景（题库匹配部分题目 + LLM 回答其余题目）时，自动填入按钮无法正常工作。

### Interview Summary

**Key Discussions**:
- **根因定位**: `combined`（442行，正确格式 `[1] A\n[2] B`）被丢弃；`finalAnswer`（445-449行）使用展示格式，含 "📋 题库匹配：" 标题和 LLM 原始输出，`parseAnswerPairs` 无法解析
- **修复方案**: 简化统一 —— `finalAnswer` 直接使用 `combined` 格式，`questionSources` 已独立展示来源，分节标题冗余
- **分支范围**: 3 个分支全部修复（混合 L1+L4、纯 L1、纯 L4）
- **冲突优先**: L1（题库）覆盖 L4（LLM），即 `l4Parsed + l1Answers`
- **测试策略**: TDD with JUnit 5

**Research Findings**:
- `parseAnswerPairs` 使用 `ANSWER_PARSE_REGEX`，能正确解析 `[N] answer` 格式 ✅
- 纯 L1 路径（第39行）已使用正确格式 —— 可作为回归基线 ✅
- `questionSources: Map<Int, String>` 在 Sidebar 中独立展示来源，不依赖 answer 文本 ✅
- L4-only 分支（第449行 `else l4Answer`）传原始 LLM 输出 —— 同样会破坏自动填入 ❌
- `callLLM()`（316-376行）为未使用代码，不在本次范围

### Metis Review

**Identified Gaps** (addressed):
- **L4-only 分支也需要修复**: 已确认，3 个分支全部修复
- **L1 vs L4 冲突优先级**: 已确认，L1 优先（`l4Parsed + l1Answers`）
- **SidebarStateRenderer bold regex `[A-D]` vs `[A-F]`**: 已有问题，不在本次范围
- **`parseL4Answer` 双反斜杠 bug**: 不在本次范围

---

## Work Objectives

### Core Objective
修复 `callLLMAndCombine()` 中 3 个场景的 `finalAnswer` 输出格式，使自动填入在任意匹配组合下都能正常工作。

### Concrete Deliverables
- `SolvePipeline.kt`: `formatCombinedAnswer()` 纯函数 + `callLLMAndCombine()` 第445-449行替换
- `SolvePipelineTest.kt`: 新增 TDD 测试（格式函数 + 集成测试）
- `AccessibilityParseUtilsTest.kt`: 新增多行组合格式解析验证

### Definition of Done
- [ ] `gradlew app:testDebugUnitTest` → BUILD SUCCESSFUL, 0 failures
- [ ] 所有 3 个分支输出格式为 `[N] answer`，每行一个答案
- [ ] `parseAnswerPairs` 能完整解析组合格式输出
- [ ] L1 答案覆盖 L4 同题号答案
- [ ] 纯 L1 路径回归测试通过（无变化）

### Must Have
- `finalAnswer` 统一为 `[N] answer` 格式，支持字母+判断题（正确/错误）
- L1 优先于 L4 的冲突解决
- 所有 3 个分支（混合/纯L1/纯L4）修复
- TDD 测试覆盖全部 3 个分支 + 回归测试

### Must NOT Have (Guardrails)
- 不修改 `parseL4Answer` 正则（LLM 解析逻辑不动）
- 不修改 `SidebarStateRenderer`（bold regex 已有问题，独立处理）
- 不修改 `ExamAccessibilityService`（auto-fill 消费逻辑不动）
- 不修改 `callLLM()` 未使用代码
- 不修改纯 L1 早期返回路径（第38-48行）
- 不引入新答案格式（JSON/Markdown 等）
- 不重构 `callLLMAndCombine` 函数结构

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: YES
- **Automated tests**: TDD
- **Framework**: JUnit 5 + Gradle (`app:testDebugUnitTest`)
- **TDD Flow**: RED (failing test) → GREEN (minimal impl) → REFACTOR

### QA Policy
Every task includes agent-executed QA scenarios.
Evidence saved to `.omo/evidence/task-{N}-{scenario-slug}.txt`.

- **Backend/Unit**: Use Bash (gradlew) - Run tests, assert BUILD SUCCESSFUL, capture output
- **API/Logic**: Use unit test assertions - assertEquals exact expected format strings

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately - TDD tests in parallel):
├── Task 1: parseAnswerPairs 多行格式解析测试 [quick]
├── Task 2: formatCombinedAnswer TDD 测试 [quick]
└── Task 3: callLLMAndCombine 集成测试 [quick]

Wave 2 (After Wave 1 - implementation):
├── Task 4: 实现 formatCombinedAnswer [quick]
└── Task 5: 接入 callLLMAndCombine [quick]

Wave FINAL (After ALL tasks):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
```

**Critical Path**: Task 1,2,3 → Task 4 → Task 5 → F1-F4
**Max Concurrent**: 3 (Wave 1)

### Agent Dispatch Summary
- **Wave 1**: 3 × `quick` — Task 1-3
- **Wave 2**: 2 × `quick` — Task 4-5
- **FINAL**: 4 × review agents — F1-F4

---

## TODOs

- [ ] 1. TDD 测试：`parseAnswerPairs` 多行组合格式解析验证

  **What to do**:
  - 在 `AccessibilityParseUtilsTest.kt` 中新增测试方法
  - 验证 `parseAnswerPairs` 能正确解析多行组合格式（模拟 `combined` 输出）
  - 覆盖场景：单选、多选、判断题混合、非连续题号
  - **TDD Phase**: RED — 确认当前 `parseAnswerPairs` 逻辑是否正确（这些测试可能直接 PASS，因为格式本身是兼容的；如果 FAIL 则说明 parseAnswerPairs 也有兼容问题）

  **测试用例**:
  ```kotlin
  @Test fun `parseAnswerPairs extracts from multi-line combined format`() {
      val input = "[1] A\n[2] B C\n[3] 正确"
      val expected = listOf(1 to listOf("A"), 2 to listOf("B", "C"), 3 to listOf("正确"))
      assertEquals(expected, parseAnswerPairs(input))
  }

  @Test fun `parseAnswerPairs handles mixed letters and true-false`() {
      val input = "[1] A\n[2] 错误\n[3] C D"
      val expected = listOf(1 to listOf("A"), 2 to listOf("错误"), 3 to listOf("C", "D"))
      assertEquals(expected, parseAnswerPairs(input))
  }

  @Test fun `parseAnswerPairs handles non-sequential question numbers`() {
      val input = "[3] A\n[1] B\n[2] C"
      // 注意：parseAnswerPairs 按正则扫描顺序返回，不排序
      val result = parseAnswerPairs(input)
      assertEquals(3, result.size)
      // result 的顺序取决于正则匹配顺序，[1] 和 [2], [3] 都会匹配
  }
  ```

  **Must NOT do**:
  - 不要修改 `parseAnswerPairs` 实现（如果测试 FAIL，记录下来作为发现的问题）
  - 不要修改 `ANSWER_PARSE_REGEX`

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
  - **Reason**: Single file, existing test patterns to follow, ~3 test methods

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3)
  - **Blocks**: None (purely additive test)
  - **Blocked By**: None

  **References**:
  - `app/src/test/java/com/examhelper/app/util/AccessibilityParseUtilsTest.kt` — 现有测试模式（`parseAnswerPairs extracts 正确` 等），参考其 assertion 风格和 import 结构
  - `app/src/main/java/com/examhelper/app/util/AccessibilityParseUtils.kt:4-16` — `parseAnswerPairs` 实现，理解其返回类型和解析逻辑
  - `app/src/main/java/com/examhelper/app/util/ExamConstants.kt:15-16` — `ANSWER_PARSE_REGEX` 定义，确认支持的格式

  **Acceptance Criteria**:
  - [ ] 新增测试方法通过（或记录 FAIL 原因）
  - [ ] `gradlew app:testDebugUnitTest --tests "AccessibilityParseUtilsTest"` → BUILD SUCCESSFUL

  **QA Scenarios**:

  ```
  Scenario: parseAnswerPairs 正确解析多行组合格式
    Tool: Bash (gradlew)
    Preconditions: 测试文件已创建，`[1] A\n[2] B C\n[3] 正确` 作为测试输入
    Steps:
      1. 运行: .\gradlew.bat app:testDebugUnitTest --tests "AccessibilityParseUtilsTest"
      2. 验证 BUILD SUCCESSFUL
      3. 验证所有 parseAnswerPairs 相关测试 PASS
    Expected Result: 测试通过，parseAnswerPairs 返回 [(1, [A]), (2, [B, C]), (3, [正确])]
    Failure Indicators: 测试 FAIL 或 BUILD FAILED
    Evidence: .omo/evidence/task-1-parse-test.txt

  Scenario: parseAnswerPairs 处理判断题混合格式
    Tool: Bash (gradlew)
    Preconditions: 测试用例包含 `[2] 错误` 判断题答案
    Steps:
      1. 运行 gradlew 测试
      2. 确认 `[2] 错误` 被解析为 `(2, ["错误"])`
    Expected Result: 判断题答案正确解析，不被过滤掉
    Evidence: .omo/evidence/task-1-tf-parse.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-1-parse-test.txt` — gradlew 输出
  - [ ] `.omo/evidence/task-1-tf-parse.txt` — 判断题解析验证

  **Commit**: YES (Wave 1 测试提交组)
  - Message: `test(answer-fix): add parseAnswerPairs multi-line format tests`
  - Files: `app/src/test/java/com/examhelper/app/util/AccessibilityParseUtilsTest.kt`
  - Pre-commit: `.\gradlew.bat app:testDebugUnitTest --tests "AccessibilityParseUtilsTest"`

- [ ] 2. TDD 测试：`formatCombinedAnswer()` 格式函数

  **What to do**:
  - 在 `SolvePipelineTest.kt` 中新增测试方法
  - 定义 `formatCombinedAnswer()` 函数签名：接收 `Map<Int, String>` (L4) + `Map<Int, String>` (L1)，返回 `String`
  - TDD：先写测试（RED），函数不存在 → 编译失败
  - 然后创建函数空壳让测试能编译，但逻辑未实现 → 测试 FAIL
  - 覆盖：排序、L1 优先、空 map、单 map、判断题答案

  **测试用例**:
  ```kotlin
  @Test fun `formatCombinedAnswer sorts by question number ascending`() {
      val l4 = mapOf(3 to "C", 1 to "A")
      val l1 = mapOf(2 to "B")
      assertEquals("[1] A\n[2] B\n[3] C", SolvePipeline.formatCombinedAnswer(l4, l1))
  }

  @Test fun `formatCombinedAnswer L1 overrides L4 on same question`() {
      val l4 = mapOf(1 to "B")
      val l1 = mapOf(1 to "A")
      assertEquals("[1] A", SolvePipeline.formatCombinedAnswer(l4, l1))
  }

  @Test fun `formatCombinedAnswer empty maps produce empty string`() {
      assertEquals("", SolvePipeline.formatCombinedAnswer(emptyMap(), emptyMap()))
  }

  @Test fun `formatCombinedAnswer handles true-false answers`() {
      val l4 = mapOf(1 to "正确", 2 to "错误")
      assertEquals("[1] 正确\n[2] 错误", SolvePipeline.formatCombinedAnswer(l4, emptyMap()))
  }

  @Test fun `formatCombinedAnswer handles multi-letter answers`() {
      val l4 = mapOf(1 to "A B C", 2 to "D E")
      assertEquals("[1] A B C\n[2] D E", SolvePipeline.formatCombinedAnswer(l4, emptyMap()))
  }
  ```

  **Must NOT do**:
  - 不要在测试通过前实现 `formatCombinedAnswer()` 真正逻辑（只创建空壳让编译通过）
  - 不要修改 `callLLMAndCombine` 调用逻辑（Task 5 处理）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
  - **Reason**: 测试编写任务，复用现有 SolvePipelineTest.kt 模式和 JUnit 5

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3)
  - **Blocks**: Task 4 (formatCombinedAnswer 实现)
  - **Blocked By**: None

  **References**:
  - `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt` — 现有测试结构，参考 import、MockK 用法、测试组织方式
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:442` — `combined` 的当前构建逻辑，formatCombinedAnswer 将替换此逻辑
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:487-494` — `normalizeAnswer` 确认答案格式（正确/错误/A B C）

  **Acceptance Criteria**:
  - [ ] `formatCombinedAnswer()` 签名定义在 SolvePipeline 中（空壳）
  - [ ] 新增测试编译通过但执行 FAIL（RED —— 因为空壳返回 ""）
  - [ ] 测试覆盖：排序、L1 优先、空值、判断题、多选

  **QA Scenarios**:

  ```
  Scenario: RED 阶段 —— 测试因空壳返回 "" 而失败
    Tool: Bash (gradlew)
    Preconditions: formatCombinedAnswer 空壳存在，测试断言具体字符串
    Steps:
      1. 运行: .\gradlew.bat app:testDebugUnitTest --tests "SolvePipelineTest"
      2. 确认新增测试 FAIL（expected "[1] A" but was ""）
    Expected Result: 所有新增 formatCombinedAnswer 测试 FAIL（RED 阶段）
    Failure Indicators: 测试 PASS（说明空壳有问题）或编译 FAIL
    Evidence: .omo/evidence/task-2-red.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-2-red.txt` — RED 阶段测试失败输出

  **Commit**: YES (Wave 1 测试提交组)
  - Message: `test(answer-fix): add formatCombinedAnswer TDD tests (RED)`
  - Files: `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt`, `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`（仅空壳签名）
  - Pre-commit: `.\gradlew.bat app:compileDebugUnitTestKotlin`（确保编译通过）

- [ ] 3. TDD 测试：`callLLMAndCombine` 集成测试（MockK）

  **What to do**:
  - 在 `SolvePipelineTest.kt` 中新增集成测试
  - 使用 MockK mock `LLMClient.chatStream` 返回受控的 L4 输出
  - 使用 Turbine 监听 `ExtractedTextBus.sidebarState` 流
  - 验证 `SidebarState.Done.answer` 的格式是 `[N] answer` 组合格式
  - 覆盖 3 个分支：混合、纯 L1、纯 L4
  - **注意**: 这些测试可能因需要 Android 环境而放在 `SolvePipelineTest.kt` 中（如果已使用 Robolectric）

  **测试用例**:
  ```kotlin
  @Test fun `callLLMAndCombine mixed L1 and L4 produces combined format`() {
      // Mock: LLM returns "[2] B" for question 2
      // L1 already has {1 to "A"}
      // Expected: finalAnswer == "[1] A\n[2] B"
  }

  @Test fun `callLLMAndCombine L1 only uses combined format`() {
      // Mock: LLM returns unparseable output
      // L1 has {1 to "A", 2 to "B"}
      // Expected: finalAnswer == "[1] A\n[2] B" (no "📋" header)
  }

  @Test fun `callLLMAndCombine L4 only uses combined format`() {
      // Mock: LLM returns "[1] A\n[2] B"
      // L1 empty
      // Expected: finalAnswer == "[1] A\n[2] B" (parsed and formatted)
  }

  @Test fun `callLLMAndCombine L1 overrides L4 on same question`() {
      // Mock: LLM returns "[1] B"
      // L1 has {1 to "A"}
      // Expected: finalAnswer == "[1] A" (L1 wins, not "B")
  }
  ```

  **Must NOT do**:
  - 不要在测试中使用真实网络请求（必须 mock `LLMClient`）
  - 不要修改 `callLLMAndCombine` 现有逻辑（测试先写，等待 Task 4-5 修复）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
  - **Reason**: 集成测试编写，需熟悉 MockK。如果 SolvePipelineTest.kt 无 MockK 先例，可能需要检查现有 mock 方式

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2)
  - **Blocks**: Task 5 (wire callLLMAndCombine)
  - **Blocked By**: None

  **References**:
  - `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt` — 现有测试的 mock 方式，确认是否使用 MockK 或 manually mock
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:380-467` — `callLLMAndCombine` 完整实现，理解 mock 点（`LLMClient.chatStream`、`ExtractedTextBus.updateSidebarState`）
  - `app/src/main/java/com/examhelper/app/network/LLMClient.kt` — LLMClient 接口，确认 mock 方法签名
  - `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt` — SidebarState 和数据流，确认 Turbine 用法

  **Acceptance Criteria**:
  - [ ] 新增集成测试编译通过
  - [ ] 测试能正确 mock `LLMClient` 和捕获 `SidebarState.Done`
  - [ ] 测试在修复前应该是 RED（因为 finalAnswer 还是展示格式）

  **QA Scenarios**:

  ```
  Scenario: 集成测试 —— 验证所有 3 个分支的 answer 格式
    Tool: Bash (gradlew)
    Preconditions: MockK 配置正确，LLMClient mock 返回受控数据
    Steps:
      1. 运行: .\gradlew.bat app:testDebugUnitTest --tests "SolvePipelineTest"
      2. 确认测试状态（修复前应为 RED/FAIL）
    Expected Result: 混合和纯L4分支测试 FAIL（answer 格式不匹配），纯L1分支可能 PASS 或 FAIL
    Failure Indicators: 编译失败或 mock 配置错误
    Evidence: .omo/evidence/task-3-integration-red.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-3-integration-red.txt` — RED 阶段集成测试输出

  **Commit**: YES (Wave 1 测试提交组)
  - Message: `test(answer-fix): add callLLMAndCombine integration tests (RED)`
  - Files: `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt`
  - Pre-commit: `.\gradlew.bat app:compileDebugUnitTestKotlin`

- [ ] 4. 实现 `formatCombinedAnswer()` 纯函数

  **What to do**:
  - 在 `SolvePipeline.kt` `companion object` 中实现 `formatCombinedAnswer()`
  - 函数签名：`fun formatCombinedAnswer(l4Answers: Map<Int, String>, l1Answers: Map<Int, String>): String`
  - **核心逻辑**: `l4Answers + l1Answers`（L1 优先覆盖 L4）→ `entries.sortedBy { it.key }` → `joinToString("\n") { "[$q] $a" }`
  - TDD：此时 Task 2 的测试应从 RED 变 GREEN
  - 将函数放在 `SolvePipeline` 的 `companion object` 或顶层私有函数（方便测试直接调用）

  **实现代码**:
  ```kotlin
  companion object {
      // ... 现有常量 ...
      
      fun formatCombinedAnswer(l4Answers: Map<Int, String>, l1Answers: Map<Int, String>): String {
          return (l4Answers + l1Answers).entries.sortedBy { it.key }
              .joinToString("\n") { (q, a) -> "[$q] $a" }
      }
  }
  ```

  **Must NOT do**:
  - 不要修改 `parseL4Answer` 或 `normalizeAnswer`
  - 不要在函数内添加副作用（纯函数，输入输出）
  - 不要添加除 L1>L4 优先级外的额外排序逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
  - **Reason**: 单文件单函数，逻辑简单（1行），纯函数实现

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (with Task 5 after this)
  - **Blocks**: Task 5
  - **Blocked By**: Task 2 (需要先有测试定义签名)

  **References**:
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:442` — 当前 `combined` 构建逻辑，formatCombinedAnswer 将替换此行
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:496-499` — `companion object` 结构，函数应放在此处

  **Acceptance Criteria**:
  - [ ] `formatCombinedAnswer()` 实现完成
  - [ ] Task 2 的测试从 RED → GREEN（`gradlew app:testDebugUnitTest --tests "SolvePipelineTest"` → 所有 formatCombinedAnswer 测试 PASS）
  - [ ] `formatCombinedAnswer(l4={1→"B"}, l1={1→"A"})` → `"[1] A"`（L1 覆盖 L4）
  - [ ] `formatCombinedAnswer(l4={3→"C",1→"A"}, l1={2→"B"})` → `"[1] A\n[2] B\n[3] C"`（排序）

  **QA Scenarios**:

  ```
  Scenario: GREEN 阶段 —— 所有 formatCombinedAnswer 测试通过
    Tool: Bash (gradlew)
    Preconditions: formatCombinedAnswer 实现完成
    Steps:
      1. 运行: .\gradlew.bat app:testDebugUnitTest --tests "SolvePipelineTest"
      2. 确认所有 formatCombinedAnswer 相关测试 PASS
    Expected Result: BUILD SUCCESSFUL, 所有新增测试 GREEN
    Failure Indicators: 任何测试 FAIL
    Evidence: .omo/evidence/task-4-green.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-4-green.txt` — GREEN 阶段测试通过输出

  **Commit**: YES (Wave 2 实现提交组)
  - Message: `feat(answer-fix): add formatCombinedAnswer with L1 priority`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`
  - Pre-commit: `.\gradlew.bat app:testDebugUnitTest --tests "SolvePipelineTest"`

- [ ] 5. 接入 `callLLMAndCombine` 替换 `finalAnswer`

  **What to do**:
  - 在 `callLLMAndCombine()` 中，将第442行 `combined` 构建替换为调用 `formatCombinedAnswer()`
  - 将第445-449行的 3 分支 `finalAnswer` 替换为 `val finalAnswer = combined`
  - 保持 `questionSources` 构建逻辑不变（453-455行）
  - TDD：此时 Task 3 的集成测试应从 RED 变 GREEN

  **变更位置**:
  ```
  当前（第440-449行）:
  // Line 440-442:
  val combined = (l1Answers + l4Parsed).entries.sortedBy { it.key }
      .joinToString("\n") { (q, a) -> "[$q] $a" }

  // Line 445-449 (被替换):
  val finalAnswer = if (l1Answers.isNotEmpty() && l4Parsed.isNotEmpty()) {
      "📋 题库匹配：\n..."
  } else if (l1Answers.isNotEmpty()) {
      "📋 题库匹配：\n..."
  } else l4Answer

  修改后:
  // Line 440-442:
  val combined = formatCombinedAnswer(l4Parsed, l1Answers)  // L4 + L1, L1优先

  // Line 445:
  val finalAnswer = combined  // 统一使用干净格式
  ```

  **Must NOT do**:
  - 不要修改 `questionSources` 逻辑（453-455行）
  - 不要修改 `streamingDisplay` 逻辑（424-431行，已经使用 `[N] answer` 格式，无需改动）
  - 不要修改 `parseL4Answer` 调用
  - 不要移除 `combined` 变量（保留可读性）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
  - **Reason**: 3 行替换（combined 调用 + finalAnswer 赋值），简单直接

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after Task 4)
  - **Blocks**: None (最后一个实现任务)
  - **Blocked By**: Task 4 (需要 formatCombinedAnswer 就绪)

  **References**:
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:438-467` — 完整 `callLLMAndCombine` 上下文
  - `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt:39` — 纯L1路径的正确格式（回归基线）
  - `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt` — `SidebarState.Done` 结构，确认 `answer` 字段用途

  **Acceptance Criteria**:
  - [ ] `callLLMAndCombine` 中 `finalAnswer` 使用 `combined` 格式（`[N] answer`）
  - [ ] Task 3 集成测试全部 GREEN
  - [ ] 纯 L1 路径回归测试 PASS（无变化）
  - [ ] `gradlew app:testDebugUnitTest` → BUILD SUCCESSFUL, 0 failures

  **QA Scenarios**:

  ```
  Scenario: 完整测试套件 —— 所有测试 GREEN
    Tool: Bash (gradlew)
    Preconditions: Task 4-5 实现完成
    Steps:
      1. 运行: .\gradlew.bat app:testDebugUnitTest
      2. 确认 BUILD SUCCESSFUL
      3. 确认 0 failures, 0 errors
    Expected Result: 所有测试通过，包括新增 + 回归
    Failure Indicators: 任何测试 FAIL 或编译错误
    Evidence: .omo/evidence/task-5-all-green.txt

  Scenario: 验证 answer 格式可被 parseAnswerPairs 解析
    Tool: Bash (gradlew)
    Preconditions: callLLMAndCombine 集成测试通过
    Steps:
      1. 确认集成测试中的 SidebarState.Done.answer 是 `[N] answer` 格式
      2. 将该 answer 字符串手动输入 parseAnswerPairs 逻辑
      3. 验证返回正确的 (题号, 答案) 对
    Expected Result: parseAnswerPairs 完整解析所有题号
    Evidence: .omo/evidence/task-5-parse-verify.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-5-all-green.txt` — 完整测试套件输出
  - [ ] `.omo/evidence/task-5-parse-verify.txt` — parseAnswerPairs 验证输出

  **Commit**: YES (Wave 2 实现提交组)
  - Message: `fix(answer-fix): use combined format in callLLMAndCombine with L1 priority`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`
  - Pre-commit: `.\gradlew.bat app:testDebugUnitTest`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `gradlew app:testDebugUnitTest`. Review changed files for: empty catches, debug logging in prod, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names.
  Output: `Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Execute EVERY QA scenario from EVERY task. Test: all 3 branches output parseable format, L1 priority works, regression test passes.
  Save to `.omo/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff. Verify 1:1 — everything in spec was built, nothing beyond spec was built. Check "Must NOT do" compliance.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **1**: `test(answer-fix): add TDD tests for combined answer format` - SolvePipelineTest.kt, AccessibilityParseUtilsTest.kt
- **2**: `fix(answer-fix): use combined format in callLLMAndCombine + L1 priority` - SolvePipeline.kt

---

## Success Criteria

### Verification Commands
```bash
cd C:\Users\lasal\i_exam
.\gradlew.bat app:testDebugUnitTest
# Expected: BUILD SUCCESSFUL, 0 failures
```

### Final Checklist
- [ ] 所有 "Must Have" 实现完成
- [ ] 所有 "Must NOT Have" 未被违反
- [ ] 所有测试通过
- [ ] Gradle build 成功
