# i_exam 三项优化：测试 + 架构重构 + 性能诊断

## TL;DR

> **Quick Summary**: 为 i_exam (ExamHelper) 建立 JUnit 5 + MockK + Turbine 测试基础 → 重构代码可维护性（SidebarPanel 拆分、SolvePipeline 重构、选项类型可扩展）→ 诊断性能瓶颈，三个 Wave 依次串行推进，测试门禁确保重构不破坏现有行为。
>
> **Deliverables**:
> - `app/build.gradle.kts` — 新增 test dependencies (JUnit 5, MockK, Turbine)
> - `app/src/test/` — 核心算法单元测试 (KBEntry, SearchManager, SolvePipeline)
> - `SidebarPanel.kt` — 从 541 行拆分为按职责分离的组件
> - `SolvePipeline.kt` — 消除可变变量、提取独立方法
> - `ExamAccessibilityService.kt` — 提取纯逻辑函数到可测试工具类
> - `PERFORMANCE_REPORT.md` — 性能诊断报告（包含内存泄漏检查）
>
> **Estimated Effort**: Medium (3-5 天)
> **Execution Order**: SERIAL — Wave 1 (tests) → Wave 2 (refactor, gated by tests) → Wave 3 (profiling)
> **Critical Path**: Wave 1 (全部通过) → Wave 2 (SidebarPanel + SolvePipeline + OptionType 并行) → Wave 3 → F1-F4

---

## Context

### Original Request
用户要求优化 i_exam (ExamHelper) Android 项目，选择了三个维度：测试基础建设、架构/代码质量、性能优化。

### Interview Summary
**Key Discussions**:
- 测试框架：JUnit 5 + MockK + Turbine（非 bun test — JS 运行时不适配 Kotlin Android）
- 执行顺序：串行（测试门禁 → 架构重构 → 性能诊断）
- 性能场景：答题流水线（500条目KB + LLM）+ POI 大文件导入（10000行）
- 架构范围：3项（SidebarPanel拆分、SolvePipeline重构、选项类型可扩展）
- 本次不做：Android 集成测试、API Key 加密、ProGuard、多LLM提供商、KB 管理器协程化

**Architecture Assessment Findings**:
- `SidebarPanel.kt`：541 行大文件，含 3 个内联正则函数 + 多种 Composable 状态渲染
- `SolvePipeline.kt`：单个 `solve()` 方法包含全部 L1-L4 逻辑，`llmSource` 被 var 声明并在分支中修改
- `ExamAccessibilityService.kt`：`parseAnswerPairs()` / `countOptionsPerQuestion()` 是纯逻辑但埋在 Service 内
- `ExtractedTextBus.kt`：3 个 `var` 字段缺乏 `@Volatile`，多线程可见性无保证
- 选项正则不一致：`parseOptionMap`(A-D) vs `isOptionNode` / `parseAnswerPairs`(A-F)
- 零测试基础设施，`testImplementation` 无任何依赖

### Metis Review
**Identified Gaps** (addressed):
- **bun test 冲突**: 用户最初选 bun test，Metis 指出 JS 运行时不适配。已确认为 JUnit 5 + MockK + Turbine
- **Wave 并行风险**: 重构无测试门禁是盲改。已确认串行执行
- **@Volatile 缺失**: 已在 Wave 1 中增加修复任务
- **选项范围不一致**: 已在 Wave 2 中纳入对齐方案
- **Agent QA**: 已明确加入验证策略

---

## Work Objectives

### Core Objective
> 为 i_exam (ExamHelper) 建立测试基础 + 重构代码可维护性 + 诊断性能瓶颈，三个方向依次串行推进。

### Concrete Deliverables
- `app/build.gradle.kts` — test dependencies 配置
- `app/src/test/java/com/examhelper/app/` — 单元测试目录 + 测试文件
- `app/src/main/java/com/examhelper/app/` — 重构后的 SidebarPanel / SolvePipeline / 可选类型工具
- `PERFORMANCE_REPORT.md` — 性能诊断报告

### Definition of Done
- [ ] `./gradlew test` → BUILD SUCCESSFUL, 全部测试通过
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] SidebarPanel.kt 行数从 541 减少（已验证通过 `wc -l`）
- [ ] SolvePipeline.kt 公开 API 不变：`class SolvePipeline(context) { solve(text) }`
- [ ] 选项范围从 A-D / A-F 不一致统一为可配置
- [ ] PERFORMANCE_REPORT.md 包含场景描述、基线指标、Top 3 瓶颈

### Must Have
- [ ] Wave 1 测试通过后 Wave 2 才能开始（行为保持）
- [ ] 纯逻辑层单元测试覆盖 KBEntry trigram/jaccard、SearchManager、SolvePipeline 分支
- [ ] SidebarPanel 拆分不改变任何视觉/文字内容
- [ ] SolvePipeline 重构不改变算法逻辑和阈值常量
- [ ] 选项类型可扩展对齐 3 个函数的正则范围

### Must NOT Have (Guardrails)
- ❌ 不引入 Hilt/Koin/任何 DI 框架
- ❌ 不升级 Kotlin/AGP/Compose/Room/OkHttp 版本
- ❌ 不修改 AndroidManifest.xml、proguard-rules.pro、任何 .xml 资源
- ❌ 不修改 EdgeHandle.kt、SidebarService.kt（除非接口签名变化）
- ❌ 不在 Wave 3 中做非授权架构修改（性能报告只建议，不实施）
- ❌ API Key 加密存储、ProGuard 优化、多 LLM 提供商、KB 协程化均不在本次范围

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed.
> Acceptance criteria requiring "user manually tests/confirms" are FORBIDDEN.

### QA Policy
Every task MUST include agent-executed QA scenarios. Evidence saved to `.omo/evidence/task-{N}-{scenario-slug}.{ext}`.
- **Build verification**: `./gradlew assembleDebug` exit 0
- **Test verification**: `./gradlew test` exit 0, test count > 0
- **Code metrics**: `wc -l`, `grep -c` for pattern presence
- **No human review needed for any acceptance criterion**

---

## Execution Strategy

### Serial Execution Waves

```
Wave 1 — Test Foundation (GATES everything):
├── Task 1: Add test dependencies to build.gradle.kts
├── Task 2: KBEntry unit tests (trigram/jaccard)
├── Task 3: SearchManager unit tests
├── Task 4: SolvePipeline unit tests (L1-L4 branching)
└── Task 5: Fix @Volatile on ExtractedTextBus fields
↓ Gate: ALL tests must pass

Wave 2 — Architecture Refactoring (Parallel within wave, gated by Wave 1):
├── Task 6: Extract pure logic from SidebarPanel.kt to utility
├── Task 7: Split SidebarPanel.kt into composable components
├── Task 8: Refactor SolvePipeline.kt
├── Task 9: Align + extend option type ranges (A-D/A-F → configurable)
└── Task 10: Extract pure logic from ExamAccessibilityService.kt
↓ Gate: ALL tests still pass, build succeeds

Wave 3 — Performance Profiling:
├── Task 11: Solve pipeline + POI import profiling
└── Task 12: Memory leak + global singleton analysis
↓ Deliverable: PERFORMANCE_REPORT.md

Wave FINAL (After ALL — 4 parallel reviews):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
→ Present results → Get explicit user okay
```

### Agent Dispatch Summary
- **Wave 1 (5 tasks)**: T1 → `quick`; T2-T4 → `deep` (test logic); T5 → `quick`
- **Wave 2 (5 tasks)**: T6-T7 → `visual-engineering` (Compose); T8 → `deep` (logic); T9 → `unspecified-high` (regex/logic); T10 → `quick` (extraction)
- **Wave 3 (2 tasks)**: T11 → `deep` (profiling); T12 → `unspecified-high` (memory)
- **FINAL (4 tasks)**: F1 → `oracle`; F2 → `unspecified-high`; F3 → `unspecified-high`; F4 → `deep`

---

## TODOs

- [x] 1. **添加测试依赖到 `app/build.gradle.kts`**

  **What to do**:
  - 在 `app/build.gradle.kts` 的 dependencies 块中新增 `testImplementation` 条目
  - 添加 JUnit 5 (Jupiter): `testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")`
  - 添加 MockK: `testImplementation("io.mockk:mockk:1.13.12")`
  - 添加 Turbine (for StateFlow testing): `testImplementation("app.cash.turbine:turbine:1.1.0")`
  - 在 `android { defaultConfig { } }` 中配置 test 任务使用 JUnit Platform:
    ```kotlin
    testOptions {
        unitTests.all { useJUnitPlatform() }
    }
    ```
  - 创建测试目录结构: `app/src/test/java/com/examhelper/app/`
  - 添加一个简单的空测试文件验证配置正确
  - 验证 `./gradlew test` 通过（虽然有 0 个测试）

  **Must NOT do**:
  - ❌ 不修改 `androidTestImplementation`（未使用）
  - ❌ 不升级现有依赖版本
  - ❌ 不添加 Room/Compose 测试依赖（不在范围）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准的 Gradle 配置修改 + 目录创建
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: NO (Wave 1 gates everything)
  - **Wave**: Wave 1
  - **Blocks**: Tasks 2-5 (all depend on build config)
  - **Blocked By**: None

  **References**:
  - `app/build.gradle.kts:49-90` — 现有 dependencies 块，在末尾追加
  - `app/build.gradle.kts:12-20` — `android { defaultConfig { } }` block，在闭包内添加 testOptions

  **Acceptance Criteria**:
  - [ ] `./gradlew test` → BUILD SUCCESSFUL
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `grep -c "junit-jupiter" app/build.gradle.kts` → ≥ 1
  - [ ] `grep -c "mockk" app/build.gradle.kts` → ≥ 1
  - [ ] `grep -c "turbine" app/build.gradle.kts` → ≥ 1
  - [ ] `grep "useJUnitPlatform" app/build.gradle.kts` → match found
  - [ ] `ls app/src/test/java/com/examhelper/app/` → 目录存在

  **QA Scenarios**:
  ```
  Scenario: Test dependencies exist
    Tool: Bash (grep)
    Steps:
      1. grep for "junit-jupiter" in app/build.gradle.kts
      2. grep for "mockk" in app/build.gradle.kts
      3. grep for "turbine" in app/build.gradle.kts
      4. grep for "useJUnitPlatform" in app/build.gradle.kts
    Expected Result: All 4 patterns found
    Evidence: .omo/evidence/task-1-deps.txt

  Scenario: Build and test pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug
      2. cd /Users/like/projects/i_exam && ./gradlew test
    Expected Result: Both return exit code 0
    Evidence: .omo/evidence/task-1-build.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出验证依赖存在
  - [ ] build + test 命令输出

  **Commit**: YES
  - Message: `test: add JUnit5/MockK/Turbine dependencies and configure JUnit Platform`
  - Files: `app/build.gradle.kts`

- [x] 2. **KBEntry 单元测试**

  **What to do**:
  - 创建 `app/src/test/java/com/examhelper/app/knowledge/KBEntryTest.kt`
  - 使用 JUnit 5 (`@Test`, `@ParameterizedTest`, `assertAll`, `assertEquals`)
  - 测试 `computeTrigrams()`:
    - "abc" → `setOf("abc")`
    - "ab" (长度 < 3) → 空 set
    - 中文 "你好世界" → `setOf("你好世", "好世界")`
    - 带标点的 "a.b,c" → 标点被过滤，"abc" → `setOf("abc")`
    - 空字符串 → 空 set
    - 带空格的 "hello world" → 空格被过滤，"helloworld" → trigrams
  - 测试 `jaccard()`:
    - 相同 set → 1.0f
    - 不相交 set → 0f
    - 部分重叠 → (0, 1) 范围内
    - 任一 set 为空 → 0f
  - 测试 `computeSHA256()`:
    - 相同输入 → 相同 hash
    - 不同输入 → 不同 hash

  **Must NOT do**:
  - ❌ 不依赖 Android 框架（纯 JVM 测试）
  - ❌ 不使用 `androidx.*` 导入

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 算法类测试，需要精确的输入/输出覆盖
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 3-5)
  - **Wave**: Wave 1
  - **Blocks**: None (pure function tests)
  - **Blocked By**: Task 1

  **References**:
  - `KBEntry.kt:13-47` — KBEntry 数据类 + companion object 中的 computeTrigrams / jaccard / computeSHA256
  - `KnowledgeBaseManager.kt:21-46` — KBEntry.computeTrigrams 和 jaccard 的完整实现
  - `Test directory`: `app/src/test/java/com/examhelper/app/` (Task 1 创建)

  **Acceptance Criteria**:
  - [ ] `./gradlew test --tests "com.examhelper.app.knowledge.KBEntryTest"` → PASS
  - [ ] 测试覆盖: computeTrigrams (5+ cases) + jaccard (4+ cases) + computeSHA256 (2 cases)
  - [ ] 纯 JVM 测试，无 Android 依赖

  **QA Scenarios**:
  ```
  Scenario: KBEntryTest passes
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew test --tests "com.examhelper.app.knowledge.KBEntryTest"
    Expected Result: BUILD SUCCESSFUL, tests pass
    Evidence: .omo/evidence/task-2-kbentry-test.txt

  Scenario: Test count verification
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew test --info 2>&1 | grep -E "Test run|KBEntryTest"
    Expected Result: Shows ≥ 10 individual test cases
    Evidence: .omo/evidence/task-2-kbentry-count.txt
  ```

  **Evidence to Capture**:
  - [ ] 测试运行输出
  - [ ] 测试用例数量

  **Commit**: YES (with Tasks 1, 3, 4, 5)
  - Message: `test: add JUnit5/MockK/Turbine + core unit tests for KBEntry, SearchManager, SolvePipeline`
  - Files: `app/src/test/java/com/examhelper/app/knowledge/KBEntryTest.kt`

- [x] 3. **SearchManager 单元测试**

  **What to do**:
  - 创建 `app/src/test/java/com/examhelper/app/pipeline/SearchManagerTest.kt`
  - 使用 JUnit 5 + MockK
  - 测试 `extractSearchQueries()`:
    - 包含括号的行 → 提取括号前的题干文本
    - 无括号但长度 > 8 的行 → fallback 模式
    - 空文本 → 空列表
    - 只有短行 (≤8 char) → 空列表
    - 混合文本 → 去重后返回非重复查询
  - 测试 `searchQuestions()`:
    - TavilyClient = null → `SearchEnhancement(skipped = true)`
    - TavilyClient.search 成功 → `SearchEnhancement(found = true, summary, references)`
    - TavilyClient.search 失败 → `SearchEnhancement(failed = true)`
    - 无需 real network — 使用 MockK mock TavilyClient

  **Must NOT do**:
  - ❌ 不进行真实网络调用（使用 MockK mock）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 逻辑分支测试 + MockK mock 对象
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 2, 4, 5)
  - **Wave**: Wave 1
  - **Blocks**: None
  - **Blocked By**: Task 1

  **References**:
  - `SearchManager.kt:52-79` — `extractSearchQueries()` 完整实现
  - `SearchManager.kt:16-50` — `searchQuestions()` 完整实现
  - `SearchManager.kt:6-12` — `SearchEnhancement` 数据类

  **Acceptance Criteria**:
  - [ ] `./gradlew test --tests "com.examhelper.app.pipeline.SearchManagerTest"` → PASS
  - [ ] extractSearchQueries 覆盖 5 种输入情况
  - [ ] searchQuestions 覆盖 skipped/success/failure 3 种结果

  **QA Scenarios**:
  ```
  Scenario: SearchManagerTest passes
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew test --tests "com.examhelper.app.pipeline.SearchManagerTest"
    Expected Result: BUILD SUCCESSFUL, tests pass
    Evidence: .omo/evidence/task-3-searchmanager-test.txt
  ```

  **Evidence to Capture**:
  - [ ] 测试运行输出

  **Commit**: YES (grouped with Task 1, 2, 4, 5)

- [x] 4. **SolvePipeline 单元测试**

  **What to do**:
  - 创建 `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt`
  - 使用 JUnit 5 + MockK + Turbine
  - 核心挑战：SolvePipeline 依赖 5 个全局单例对象：
    - `ExamApplication.instance.appConfig.getSnapshot()`
    - `KnowledgeBaseManager.activeKB?.search(...)`
    - `KBEngine` (构造函数创建)
    - `LLMClient` (方法内创建)
    - `ExtractedTextBus` (全局 object)
  - 使用 MockK 的 `mockkObject()` 来 mock `ExtractedTextBus`
  - 对于 `KBEngine` 和 `LLMClient`，这些在方法内部创建的实例需要用 `mockkConstructor()` 或在测试中重构为参数注入
  - **简化方案**：将 SolvePipeline 从构造函数接收 `KBEngine` 参数（不破环现有调用），然后注入 mock
    ```kotlin
    // 重构建议：将 SolvePipeline 改为
    class SolvePipeline(context: Context, private val kbEngine: KBEngine = KBEngine(context))
    ```
  - 测试场景：
    - L1 Excel 命中 (score ≥ 0.70) → Done(EXCEL_MATCH)
    - L2 Wiki 命中 (score ≥ 0.50) → Done(KB_MATCH)
    - L1 部分匹配 + L2 部分匹配 → L4 LLM_DIRECT 或 KB_INFER
    - 搜索层在 API Key 空白时跳过
  - 使用 Turbine 验证 `ExtractedTextBus.sidebarState` 的 StateFlow 发射顺序

  **Must NOT do**:
  - ❌ 不修改 SolvePipeline.kt 的公开 API（除了增加可选参数 `kbEngine` 的构造函数重载）
  - ❌ 不依赖 Android Instrumentation 测试（纯 JVM）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 最复杂的测试——需要 mock 全局单例 + 验证 StateFlow 发射
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 2, 3, 5)
  - **Wave**: Wave 1
  - **Blocks**: None
  - **Blocked By**: Task 1

  **References**:
  - `SolvePipeline.kt:16-172` — 完整实现
  - `SolvePipeline.kt:20-35` — L1 逻辑 (Excel 匹配 early-return)
  - `SolvePipeline.kt:39-55` — L2 逻辑 (Wiki 匹配 early-return)
  - `SolvePipeline.kt:57-85` — L3 上下文构建 + L4 LLM 调用
  - `ExtractedTextBus.kt:23-31` — SidebarState sealed class
  - `ExtractedTextBus.kt:44-45` — sidebarState 的 StateFlow

  **Acceptance Criteria**:
  - [ ] `./gradlew test --tests "com.examhelper.app.pipeline.SolvePipelineTest"` → PASS
  - [ ] 测试覆盖 L1 early-return / L2 early-return / LLM fallback / search skip 场景
  - [ ] 使用 Turbine 验证 StateFlow 状态转换

  **QA Scenarios**:
  ```
  Scenario: SolvePipelineTest passes
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew test --tests "com.examhelper.app.pipeline.SolvePipelineTest"
    Expected Result: BUILD SUCCESSFUL, tests pass
    Evidence: .omo/evidence/task-4-solvepipeline-test.txt
  ```

  **Evidence to Capture**:
  - [ ] 测试运行输出

  **Commit**: YES (grouped with Tasks 1, 2, 3, 5)

- [x] 5. **修复 `ExtractedTextBus` 线程安全性**

  **What to do**:
  - 在 `ExtractedTextBus.kt` 中，为 3 个 mutable var 字段添加 `@Volatile` 注解
  - 修改：
    ```kotlin
    @Volatile
    var lastTokensPerSec: Float = 0f
    @Volatile
    var lastPromptTokens: Int = 0
    @Volatile
    var lastTtftMs: Long = 0L
    ```
  - 这些字段从 `Dispatchers.IO` (LLM 流式回调) 写入，从 `Dispatchers.Main` (Compose) 读取
  - `@Volatile` 确保写入立即可见于所有线程

  **Must NOT do**:
  - ❌ 不修改其他非 volatile 字段
  - ❌ 不改为 AtomicReference（简单 volatile 即可满足需求）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 3 行注解添加，零风险
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 2, 3, 4)
  - **Wave**: Wave 1
  - **Blocks**: None
  - **Blocked By**: Task 1

  **References**:
  - `ExtractedTextBus.kt:11-13` — 3 个 var 字段的当前位置
  - `SolvePipeline.kt:120,138,146` — 这些字段的写入点
  - `SidebarPanel.kt:224-229` — 这些字段的读取点

  **Acceptance Criteria**:
  - [ ] `grep "@Volatile" app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt` → 3 个匹配
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL (Wave 1 测试仍然通过)

  **QA Scenarios**:
  ```
  Scenario: @Volatile annotations exist
    Tool: Bash (grep)
    Steps:
      1. grep -c "@Volatile" app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt
    Expected Result: 3
    Evidence: .omo/evidence/task-5-volatile.txt

  Scenario: Build and tests still pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug && ./gradlew test
    Expected Result: Both exit 0
    Evidence: .omo/evidence/task-5-build-test.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出
  - [ ] build + test 输出

  **Commit**: YES (grouped with Tasks 1-4)

- [x] 6. **从 SidebarPanel.kt 提取纯逻辑到工具文件**

  **What to do**:
  - 创建新的工具文件: `app/src/main/java/com/examhelper/app/util/OptionTextUtils.kt`
  - 将 `SidebarPanel.kt` 中的 2 个纯逻辑函数移动到新文件：
    - `parseOptionMap(text: String): Map<String, String>` (lines 482-492)
    - `appendOptionText(line: String, optionMap: Map<String, String>): String` (lines 495-510)
  - 将函数改为 `internal` 或 `public` 可见性
  - 在 `SidebarPanel.kt` 中更新 import
  - 提取后 `SidebarPanel.kt` 减少约 30 行纯逻辑代码
  - 为 OptionTextUtils 添加单元测试 `app/src/test/java/com/examhelper/app/util/OptionTextUtilsTest.kt`

  **Must NOT do**:
  - ❌ 不改动 Composable 函数结构或 UI 渲染逻辑
  - ❌ 不改动字符串常量或视觉外观
  - ❌ 不修改其他文件

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 纯机械提取，模式明确
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 7, 8, 9, 10)
  - **Wave**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1 (ALL tests must pass)

  **References**:
  - `SidebarPanel.kt:482-510` — 2 个待提取的纯逻辑函数
  - `SidebarPanel.kt:284` — `remember(s.text)` 中对 `parseOptionMap` 的调用

  **Acceptance Criteria**:
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL (Wave 1 tests + new OptionTextUtils tests pass)
  - [ ] `grep "fun parseOptionMap" app/src/main/java/com/examhelper/app/util/OptionTextUtils.kt` → match
  - [ ] `grep "fun appendOptionText" app/src/main/java/com/examhelper/app/util/OptionTextUtils.kt` → match
  - [ ] `grep "parseOptionMap" app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt` → import only, not definition

  **QA Scenarios**:
  ```
  Scenario: Pure logic extracted
    Tool: Bash (grep)
    Steps:
      1. grep for "fun parseOptionMap" in OptionTextUtils.kt
      2. grep for "fun appendOptionText" in OptionTextUtils.kt
      3. grep for "fun parseOptionMap" in SidebarPanel.kt — should be 0 (only import remains)
    Expected Result: Functions exist in utility file, removed from SidebarPanel.kt
    Evidence: .omo/evidence/task-6-extraction.txt

  Scenario: Build + tests pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug && ./gradlew test
    Expected Result: Both exit 0
    Evidence: .omo/evidence/task-6-build-test.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出
  - [ ] build + test 输出

  **Commit**: YES (with Tasks 7)
  - Message: `refactor(sidebar): extract pure logic to OptionTextUtils.kt`
  - Files: `app/src/main/java/com/examhelper/app/util/OptionTextUtils.kt`, `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt`

- [x] 7. **拆分 SidebarPanel.kt 为多个 Composable 组件**

  **What to do**:
  - 将 `SidebarPanel.kt`（541 行）按职责拆分为以下文件：
    - `ui/sidebar/SidebarPanel.kt` — 主面板结构（标题栏 + 内容区 + 底部状态栏），约保留 150 行
    - `ui/sidebar/SidebarStateRenderer.kt` — 各状态渲染（Idle/Loading/Preview/Streaming/Done/Error），从 `when (state)` 分支提取
    - `ui/sidebar/SidebarActions.kt` — 操作按钮（读取屏幕、解答、重新解答、保存到题库、自动填入）
    - `ui/sidebar/SidebarComponents.kt` — 共享组件（SectionHeader、StatusHint、StatusBanner）
  - 拆分原则：
    - **不改变任何视觉/文字内容**（字符精确匹配）
    - **不改变函数签名**（SidebarPanel(onHide) 公开 API 不变）
    - 内部 import 链接新组件文件
  - 拆分后 SidebarPanel.kt 行数应 < 200 行

  **Must NOT do**:
  - ❌ 不改动任何 UI 文本或样式（MaterialTheme colors, typography, padding etc.）
  - ❌ 不改动 SidebarService.kt 或 EdgeHandle.kt（被调用的 SidebarPanel(onHide) 签名不变）
  - ❌ 不新增或移除任何功能

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose 组件拆分，需要精确保持 UI 一致性
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 6, 8, 9, 10)
  - **Wave**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `SidebarPanel.kt:70-541` — 完整源文件
  - `SidebarPanel.kt:82-122` — 顶部标题栏（保留在 SidebarPanel）
  - `SidebarPanel.kt:128-415` — 状态渲染（→ SidebarStateRenderer）
  - `SidebarPanel.kt:145-205` — 操作按钮（→ SidebarActions）
  - `SidebarPanel.kt:445-538` — 共享组件（→ SidebarComponents）

  **Acceptance Criteria**:
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL
  - [ ] `wc -l app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt` → < 200
  - [ ] 4 个新文件存在：SidebarStateRenderer.kt, SidebarActions.kt, SidebarComponents.kt
  - [ ] `grep "fun SidebarPanel" app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt` → 唯一公开 API
  - [ ] SidebarService.kt 中 `SidebarPanel(onHide = { hidePanel() })` 调用不变

  **QA Scenarios**:
  ```
  Scenario: SidebarPanel line count reduced
    Tool: Bash (wc)
    Steps:
      1. wc -l app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt
    Expected Result: < 200 lines
    Evidence: .omo/evidence/task-7-lines.txt

  Scenario: All new files exist
    Tool: Bash (ls)
    Steps:
      1. ls app/src/main/java/com/examhelper/app/ui/sidebar/
    Expected Result: SidebarPanel.kt, SidebarStateRenderer.kt, SidebarActions.kt, SidebarComponents.kt all present
    Evidence: .omo/evidence/task-7-files.txt

  Scenario: Build passes
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug
    Expected Result: BUILD SUCCESSFUL
    Evidence: .omo/evidence/task-7-build.txt
  ```

  **Evidence to Capture**:
  - [ ] wc -l 输出
  - [ ] 文件列表
  - [ ] build 输出

  **Commit**: YES (with Task 6)
  - Message: `refactor(sidebar): split SidebarPanel.kt into 4 composable component files`
  - Files: `app/src/main/java/com/examhelper/app/ui/sidebar/*.kt`

- [x] 8. **重构 SolvePipeline.kt**

  **What to do**:
  - 重构 `SolvePipeline.kt`（172 行），不改变公开 API 签名的前提下提升可读性：
  - **消除可变变量 `llmSource`**:
    - 当前: `var llmSource = AnswerSource.LLM_DIRECT` 在多个分支中修改
    - 改为: 在最终决定时直接计算 source，不经过可变变量
  - **提取私有方法**:
    - `private suspend fun tryExcelMatch(text: String): Pair<AnswerSource, String>?` — L1
    - `private suspend fun tryWikiMatch(text: String): Pair<AnswerSource, String>?` — L2
    - `private suspend fun trySearchEnhancement(config: ConfigSnapshot, text: String): SearchEnhancement` — L3
    - `private suspend fun callLLM(config: ConfigSnapshot, effectiveMessage: String, requestStartMs: Long): Pair<String, AnswerSource>` — L4
  - **简化条件分支**:
    - 当前 `solve()` 方法有一个约 70 行的顺序判断链
    - 改为 `return` early / `?.let {}` / `Elvis operator` 链式风格
  - 核心算法逻辑、阈值常量（0.70f, 0.50f, 0.40f, 0.20f）保持不变

  **Must NOT do**:
  - ❌ 不改变任何阈值常量（0.70f, 0.50f, 0.40f, 0.20f, 0.15f）
  - ❌ 不改变 SolvePipeline 类的公开 API
  - ❌ 不改动 CancellationException rethrow 行为
  - ❌ 不修改其他文件

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 核心业务逻辑重构，需精确分离关注点
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 6, 7, 9, 10)
  - **Wave**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `SolvePipeline.kt:20-25` — 入口方法 `solve(text)` 签名
  - `SolvePipeline.kt:26-37` — L1 Excel 匹配
  - `SolvePipeline.kt:39-55` — L2 Wiki 匹配
  - `SolvePipeline.kt:57-85` — 上下文构建（L3 + KB_INFER）
  - `SolvePipeline.kt:87-111` — L3 Tavily 搜索
  - `SolvePipeline.kt:113-166` — L4 LLM 调用 + Done 发射

  **Acceptance Criteria**:
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL (SolvePipelineTest 仍然通过)
  - [ ] `grep "class SolvePipeline" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt` → 公开 API 不变
  - [ ] `grep "fun solve" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt` → 公开方法不变
  - [ ] 不再有 `var llmSource`（改为 val 或直接计算）
  - [ ] 至少 4 个私有 helper 方法

  **QA Scenarios**:
  ```
  Scenario: Build + Wave 1 tests pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug && ./gradlew test
    Expected Result: Both exit 0
    Evidence: .omo/evidence/task-8-build-test.txt

  Scenario: No mutable llmSource var
    Tool: Bash (grep)
    Steps:
      1. grep "var llmSource" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
    Expected Result: No match (0)
    Evidence: .omo/evidence/task-8-var.txt

  Scenario: Private helper methods exist
    Tool: Bash (grep)
    Steps:
      1. grep "private.*fun" app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt
    Expected Result: ≥ 4 matches
    Evidence: .omo/evidence/task-8-methods.txt
  ```

  **Evidence to Capture**:
  - [ ] build + test 输出
  - [ ] grep 输出 (var 移除)
  - [ ] grep 输出 (新方法数)

  **Commit**: YES
  - Message: `refactor(pipeline): extract methods, eliminate mutable var in SolvePipeline`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`

- [x] 9. **对齐并扩展选项类型范围**

  **What to do**:
  - **问题**: 当前 3 个函数使用不一致的选项范围:
    - `parseOptionMap` (SidebarPanel.kt:484): `A-D` 正则
    - `isOptionNode` (ExamAccessibilityService.kt:256): `A-F`
    - `parseAnswerPairs` (ExamAccessibilityService.kt:305): `A-F`
  - **步骤**:
    1. 创建一个新的配置文件/常量文件 `util/ExamConstants.kt`：
       ```kotlin
       object ExamConstants {
           var OPTION_LETTERS = 'A'..'F'
           val OPTION_RANGE_REGEX: Regex
               get() = Regex("""^[${OPTION_LETTERS.first}-${OPTION_LETTERS.last}]\s*[.、:：)）]""")
           val ANSWER_PARSE_REGEX: Regex
               get() = Regex("""[\[【]?(\d+)[\]】]?\s*([${OPTION_LETTERS.first}-${OPTION_LETTERS.last}\s、,，;]+|正确|错误|对|错)""")
       }
       ```
    2. 更新 `SidebarPanel.kt` 中的 `parseOptionMap`: 范围从 `A-D` 改为 `A-F` (使用常量)
    3. 更新 `ExamAccessibilityService.kt` 中的 `isOptionNode` (确认已是 `A-F`，改为引用常量)
    4. 更新 `ExamAccessibilityService.kt` 中的 `parseAnswerPairs` (确认已是 `A-F`，改为引用常量)
    5. 修复 `extractFrontmatter` in `KBEngine.kt:303-326`: 当前解析使用行号索引,不够精确。改为找到第一个 `---` + 第二个 `---` 的精确匹配
  - 添加测试验证 `ExamConstants.OPTION_RANGE_REGEX` 可以扩展为 `A-G`
  - 更新 `SidebarPanel.kt` 中 `parseOptionMap` 的对应 import

  **Must NOT do**:
  - ❌ 不修改自动点击逻辑（performAutoClick 的节点遍历行为不变）
  - ❌ 不改动 countOptionsPerQuestion 函数

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 涉及 3 个文件的正则一致性 + KBEngine 的 frontmatter 修复
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 6, 7, 8, 10)
  - **Wave**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `SidebarPanel.kt:482-492` — `parseOptionMap` (A-D 范围)
  - `ExamAccessibilityService.kt:255-258` — `isOptionNode` (A-F 范围)
  - `ExamAccessibilityService.kt:304-317` — `parseAnswerPairs` (A-F 范围)
  - `KBEngine.kt:303-326` — `extractFrontmatter` (行号索引解析)
  - `KBEngine.kt:264-301` — `parseWikiPages` (frontmatter 的调用方)

  **Acceptance Criteria**:
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL (包括新的 ExamConstants 测试)
  - [ ] `ExamConstants.OPTION_LETTERS == 'A'..'F'`
  - [ ] 将 `OPTION_LETTERS` 改为 `'A'..'G'` 后 build 和测试仍然通过（验证可扩展性）
  - [ ] 3 个函数使用同一个范围来源
  - [ ] `extractFrontmatter` 不再使用行号计数做决策

  **QA Scenarios**:
  ```
  Scenario: Option ranges aligned
    Tool: Bash (grep)
    Steps:
      1. grep "A-D" app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt — should be removed
      2. grep "ExamConstants.OPTION" app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt — constant reference
      3. grep "ExamConstants.OPTION" app/src/main/java/com/examhelper/app/service/ExamAccessibilityService.kt — constant reference
    Expected Result: No hardcoded A-D, all use ExamConstants
    Evidence: .omo/evidence/task-9-ranges.txt

  Scenario: Build + tests pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug && ./gradlew test
    Expected Result: Both exit 0
    Evidence: .omo/evidence/task-9-build-test.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出
  - [ ] build + test 输出

  **Commit**: YES (with Task 10)
  - Message: `feat: align option ranges (A-D/A-F) via ExamConstants + fix extractFrontmatter`
  - Files: `app/src/main/java/com/examhelper/app/util/ExamConstants.kt`, `SidebarPanel.kt`, `ExamAccessibilityService.kt`, `KBEngine.kt`

- [x] 10. **从 ExamAccessibilityService.kt 提取纯逻辑函数**

  **What to do**:
  - `ExamAccessibilityService.kt`（372 行）是一个 Android Service，但其中包含几个纯逻辑函数
  - 将这些纯逻辑函数提取到 `util/AccessibilityParseUtils.kt`：
    - `parseAnswerPairs(answer: String): List<Pair<Int, List<String>>>` (lines 304-317)
    - `countOptionsPerQuestion(sourceText: String): List<Int>` (lines 319-337)
    - `matchesSelection(nodeText: String, selection: String): Boolean` (lines 339-349)
  - 提取后 `ExamAccessibilityService.kt` 调用这些函数
  - 在 `ExamAccessibilityService.kt` 中保留 `isOptionNode`（因它也会用于自动点击流程中）
  - **不提取**: `performAutoClick`、`clickToggleOption`、`findConfirmButton`、`findAllClickable`（这些都依赖 AccessibilityNodeInfo，有 Android 框架依赖）
  - 为提取的函数添加单元测试

  **Must NOT do**:
  - ❌ 不修改 `performAutoClick` / `clickToggleOption` / `findAllClickable` / `searchMatches` 等 Android 相关逻辑
  - ❌ 不修改 `findConfirmButton` 函数
  - ❌ 不修改 `cleanAndFormat` 函数

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 纯机械提取，模式明确
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Tasks 6, 7, 8, 9)
  - **Wave**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Wave 1

  **References**:
  - `ExamAccessibilityService.kt:304-317` — `parseAnswerPairs`
  - `ExamAccessibilityService.kt:319-337` — `countOptionsPerQuestion`
  - `ExamAccessibilityService.kt:339-349` — `matchesSelection`
  - `ExamAccessibilityService.kt:160-235` — `performAutoClick`（不提取，Android 依赖）

  **Acceptance Criteria**:
  - [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
  - [ ] `./gradlew test` → BUILD SUCCESSFUL (包括新提取函数的测试)
  - [ ] 3 个函数从 ExamAccessibilityService.kt 移动到工具文件
  - [ ] ExamAccessibilityService.kt 中通过 import + delegation 调用提取的函数

  **QA Scenarios**:
  ```
  Scenario: Pure logic extracted from service
    Tool: Bash (grep)
    Steps:
      1. grep -c "fun parseAnswerPairs" app/src/main/java/com/examhelper/app/util/AccessibilityParseUtils.kt — should be 1
      2. grep -c "fun countOptionsPerQuestion" app/src/main/java/com/examhelper/app/util/AccessibilityParseUtils.kt — should be 1
      3. grep -c "fun matchesSelection" app/src/main/java/com/examhelper/app/util/AccessibilityParseUtils.kt — should be 1
      4. grep "fun parseAnswerPairs" app/src/main/java/com/examhelper/app/service/ExamAccessibilityService.kt — should be 0
    Expected Result: Functions moved to utility, removed from Service
    Evidence: .omo/evidence/task-10-extraction.txt

  Scenario: Build + tests pass
    Tool: Bash
    Steps:
      1. cd /Users/like/projects/i_exam && ./gradlew assembleDebug && ./gradlew test
    Expected Result: Both exit 0
    Evidence: .omo/evidence/task-10-build-test.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出
  - [ ] build + test 输出

  **Commit**: YES (with Task 9)
  - Message: `refactor(service): extract pure logic from ExamAccessibilityService to utility`
  - Files: `app/src/main/java/com/examhelper/app/util/AccessibilityParseUtils.kt`, `ExamAccessibilityService.kt`

- [x] 11. **性能诊断：答题流水线 + POI 大文件导入**

  **What to do**:
  - 创建 `PERFORMANCE_REPORT.md`（放入项目根目录）
  - **场景 1: 答题流水线延迟**:
    - 准备工作: 构建一个含 500 条题目的知识库（脚本生成）
    - 测量 `SolvePipeline.solve()` 的全链路延迟（L1 + L2 + L3 + L4）
    - 记录: L1 Excel 匹配延迟、L2 Wiki FTS 搜索延迟、Tavily 搜索延迟、LLM 首字符时间 (TTFT)
    - 工具: Android Studio Profiler 或手动 `System.currentTimeMillis()` 埋点
    - 注意: LLM 调用依赖网络，如果无法连接则记录 LLM 部分为 N/A
  - **场景 2: POI 大文件导入**:
    - 准备工作: 创建一个 10000 行的 Excel 文件（A列 题目, B列 答案）
    - 测量 `KnowledgeBase.importExcel()` 的导入时间
    - 记录: 文件读取耗时、POI 解析耗时、内存使用
  - **报告结构**:
    - 测试环境（设备/模拟器、API level、内存）
    - 各场景的测试方法、基线数据
    - Top 3 性能瓶颈分析
    - 优化建议（每个建议标注：影响程度 + 实现难度）
    - 内存泄漏检查小节

  **Must NOT do**:
  - ❌ 不修改任何代码（纯诊断，产出一份报告）
  - ❌ 不添加第三方 profiling 工具（使用 AS Profiler 或手动计时）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要设计测试场景、收集数据、分析瓶颈

  **Parallelization**:
  - **Can Run In Parallel**: NO (sequential within Wave 3)
  - **Wave**: Wave 3
  - **Blocks**: Task 12 (memory check)
  - **Blocked By**: Wave 2

  **References**:
  - `SolvePipeline.kt:20-25` — `solve()` 方法
  - `KnowledgeBaseManager.kt:57-79` — `importExcel()` 方法
  - `ExamAccessibilityService.kt:141-158` — `traverseNode()` 可能的遍历瓶颈

  **Acceptance Criteria**:
  - [ ] `PERFORMANCE_REPORT.md` 文件存在
  - [ ] 报告包含 2 个场景的测试方法和基线数据
  - [ ] 报告包含 Top 3 瓶颈分析和优化建议
  - [ ] 建议按影响/难度分类

  **QA Scenarios**:
  ```
  Scenario: Performance report exists
    Tool: Bash (ls)
    Steps:
      1. ls -la /Users/like/projects/i_exam/PERFORMANCE_REPORT.md
    Expected Result: File exists
    Evidence: .omo/evidence/task-11-report-exists.txt

  Scenario: Report contains required sections
    Tool: Bash (grep)
    Steps:
      1. grep -c "场景" /Users/like/projects/i_exam/PERFORMANCE_REPORT.md
      2. grep -c "瓶颈" /Users/like/projects/i_exam/PERFORMANCE_REPORT.md
      3. grep -c "建议" /Users/like/projects/i_exam/PERFORMANCE_REPORT.md
    Expected Result: ≥ 2 matches for "场景", ≥ 1 for "瓶颈", ≥ 1 for "建议"
    Evidence: .omo/evidence/task-11-sections.txt
  ```

  **Evidence to Capture**:
  - [ ] 文件存在
  - [ ] 章节 grep 输出

  **Commit**: YES (with Task 12)
  - Message: `docs: add PERFORMANCE_REPORT.md with profiling results`
  - Files: `PERFORMANCE_REPORT.md`

- [x] 12. **内存泄漏检查 + 全局单例分析**

  **What to do**:
  - 将发现写入 `PERFORMANCE_REPORT.md` 的 "内存泄漏检查" 小节
  - **检查项**:
    1. **全局单例对象**:
       - `ExamApplication.instance` — Application 级单例，生命周期 = app 进程，SAFE
       - `KnowledgeBaseManager` — object 持有 `mutableListOf<KnowledgeBase>()`，RISK（进程恢复重建）
       - `ExtractedTextBus` — object 无成员引用泄漏风险，SAFE
    2. **协程泄漏**:
       - `ExamAccessibilityService.kt:20` — `CoroutineScope(SupervisorJob() + Dispatchers.Main)` 需确认 onDestroy 中 cancel
    3. **匿名内部类/回调泄漏**:
       - `LLMClient.kt:201-243` — `EventSourceListener` 匿名内部类，在 `awaitClose` 中 cancel，SAFE
    4. **Compose 重组泄漏**:
       - `SidebarPanel.kt:216-226` — `LaunchedEffect` 有 `key = s.startTimeMs`，SAFE
  - **检查结果格式**: 每个发现标注 LEAK/RISK/SAFE + 代码位置 + 简短分析

  **Must NOT do**:
  - ❌ 不修改代码（仅记录发现到报告）
  - ❌ 不添加 LeakCanary 依赖

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 静态代码分析，需要理解 Android 生命周期模式

  **Parallelization**:
  - **Can Run In Parallel**: NO (sequential after Task 11)
  - **Wave**: Wave 3
  - **Blocks**: None
  - **Blocked By**: Task 11

  **References**:
  - `ExamAccessibilityService.kt:20-21` — CoroutineScope 创建
  - `ExamAccessibilityService.kt:53-58` — onDestroy（检查 scope.cancel）
  - `LLMClient.kt:244-245` — `awaitClose { eventSource.cancel() }`
  - `KnowledgeBaseManager.kt:131-148` — 全局 object 持有 KB 列表

  **Acceptance Criteria**:
  - [ ] PERFORMANCE_REPORT.md 包含"内存泄漏检查"小节
  - [ ] 覆盖 4 个检查类别（全局单例/协程/匿名回调/Compose）
  - [ ] 每个发现标注 LEAK/RISK/SAFE 分类

  **QA Scenarios**:
  ```
  Scenario: Memory leak section in report
    Tool: Bash (grep)
    Steps:
      1. grep -c "内存泄漏" /Users/like/projects/i_exam/PERFORMANCE_REPORT.md
      2. grep -c "LEAK\|RISK\|SAFE" /Users/like/projects/i_exam/PERFORMANCE_REPORT.md
    Expected Result: ≥ 1 for "内存泄漏", ≥ 4 for LEAK/RISK/SAFE
    Evidence: .omo/evidence/task-12-memory.txt
  ```

  **Evidence to Capture**:
  - [ ] grep 输出

  **Commit**: YES (with Task 11)

---

## Final Verification Wave

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, curl endpoint, run command). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .omo/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew assembleDebug` + `./gradlew test`. Review all changed files for: `as any`/`@Suppress`, empty catches, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names.
  Output: `Build [PASS/FAIL] | Test [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration. Check Wave 1 gating: Wave 2 tasks should only proceed if Wave 1 tests pass.
  Output: `Scenarios [N/N pass] | Integration [N/N] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination: Task N touching Task M's files.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Tasks 1-5 (Wave 1)**: `test: add JUnit5/MockK/Turbine + core unit tests for KBEntry, SearchManager, SolvePipeline`
  - Files: `app/build.gradle.kts`, `app/src/test/java/com/examhelper/app/*`
- **Tasks 6-7 (Wave 2a)**: `refactor(sidebar): extract pure logic to OptionTextUtils + split SidebarPanel.kt into components`
  - Files: `app/src/main/java/com/examhelper/app/ui/sidebar/*.kt`, `util/OptionTextUtils.kt`
- **Task 8 (Wave 2b)**: `refactor(pipeline): extract methods, eliminate mutable var in SolvePipeline`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`
- **Tasks 9-10 (Wave 2c)**: `feat: align option ranges (A-D/A-F) via ExamConstants + extract pure logic from service`
  - Files: `util/ExamConstants.kt`, `util/AccessibilityParseUtils.kt`, `SidebarPanel.kt`, `ExamAccessibilityService.kt`, `KBEngine.kt`
- **Tasks 11-12 (Wave 3)**: `docs: add PERFORMANCE_REPORT.md with profiling and memory analysis`
  - Files: `PERFORMANCE_REPORT.md`

---

## Success Criteria

### Verification Commands
```bash
./gradlew assembleDebug  # Expected: BUILD SUCCESSFUL
./gradlew test            # Expected: BUILD SUCCESSFUL, all tests pass
wc -l app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt  # Expected: < 541
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] Build + tests pass
- [ ] Performance report delivered
