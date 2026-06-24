# Tavily 参考资料：LLM 答案下方单条摘录展示

## TL;DR

> **快速摘要**: 在悬浮侧边栏的 LLM 答案下方追加 **1 条** Tavily 搜索参考摘录（100~150 字、句末截断、自动标注 AI 答题题号），同时移除现有答案上方的多条标题+URL 引用区块。**复用 L3 已有 Tavily 结果，零新增网络请求**。
>
> **Deliverables**:
> - 新文件 `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`（纯函数工具类）
> - 修改 `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`（删除旧引用块 + 新增单条参考显示）
> - 新文件 `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt`（覆盖 8+ 边界情况）
> - 全部现有测试保持绿（`SolvePipelineTest`、`SearchManagerTest`、`AccessibilityParseUtilsTest`、`KBEntryTest`）
>
> **Estimated Effort**: Short（半天内）
> **Parallel Execution**: YES — 3 个独立 Wave-1 任务可并行
> **Critical Path**: T1（Formatter 纯函数）→ T3（UI 接入）→ T4（单元测试）→ Final Wave

---

## Context

### Original Request
> tavily 搜索 LLM 回答题目的内容，在答案全部输出完，换行写在后面。每道题只显示一条参考信息，并且只显示和题目有关的 100~150 字。

### Interview Summary

**Key Discussions（Q1–Q11，全部已确认）**:

| 编号 | 决策 | 选择 |
|---|---|---|
| Q1 | 与现有「🔍 参考资料」区块的关系 | **C 改造**（删上方多条 → 加下方单条） |
| Q2 | Tavily 数据源 | **A 复用 L3 结果**（零额外网络） |
| Q3 | 适用流水线路径 | **A 有就显示，没就不显示** |
| Q4 | 100~150 字截取策略 | **D 句末截断 + 太短跳过** |
| Q5 | 是否显示来源域名 | **不显示**（只正文） |
| Q6 | 失败/无结果处理 | **A 静默** |
| Q7 | 启用开关 | **C 共用 Tavily Key 字段** |
| Q8 | 测试策略 | **B 改完后补测试** |
| Q9 | 多题混合时是否标注题号 | **B 自动标注**（`🔍 参考（题 3, 4, 5）: ...`） |
| Q10 | "每题真正一条" vs "全局一条" | **C 接受全局一条** + Q9 题号标注 |
| Q11 | 长文本无句末标点的 fallback | **B `take(150) + "..."`** |

### Research Findings

- **项目**: i_exam / 考试助手 (ExamHelper) — Android Kotlin 单模块项目，包名 `com.examhelper.app`
- **架构**: Jetpack Compose（无 XML、无 Markdown 库）+ Material3 + 单 Activity + 悬浮侧边栏（Accessibility）
- **DI**: 无（手动 `ExamApplication.instance` 单例）
- **Tavily 已集成**: `TavilyClient.kt` + `SearchManager.kt`，作为 L3 阶段在 LLM 之前预搜索；结果已通过 `SidebarState.Done.references` 传到 UI
- **关键代码切入点（已 Oracle 阶段一验证）**:
  - `SidebarStateRenderer.kt:100-202` Done 状态分支
  - `SidebarStateRenderer.kt:106-135` questionSources 显示（**保留不动**）
  - `SidebarStateRenderer.kt:146-184` 旧多条引用块（**删除**）
  - `SidebarStateRenderer.kt:185-197` 答案 `Text` 逐行渲染（保留）
  - `SidebarStateRenderer.kt:199` `ReworkButton`（其前插入新参考）
  - `Theme.kt:16` `TextSecondary = Color(0xFF9CA3AF)`
  - `TavilyClient.kt:78-82` `Reference(title, url, snippet)` 数据类
  - `ExtractedTextBus.kt:32` `SidebarState.Done(text, answer, source, references, questionSources)`
- **测试**: JUnit 5 + MockK 1.13 + Turbine（仅 JVM 单元测试，无 MockWebServer，无 AndroidX Test）
- **minSdk**: 26（`Html.fromHtml(String, Int)` 自 API 24 起可用）
- **Performance**: `PERFORMANCE_REPORT.md` 明确 L3 (Tavily) 是**第一性能瓶颈**——本计划 Q2=A 复用 L3 结果**完全符合**该约束

### Metis Review

**已确认的 Gap & 自动应用的修复**:
- ✅ Tavily snippet 可能含 HTML 标签 → **使用 stdlib 正则清洗**（`Regex("<[^>]+>")` + 手动 HTML 实体替换），**不依赖 `android.text.Html`**（项目无 Robolectric，单元测试需在纯 JVM 跑）
- ✅ 句末截断的 `.` 误匹配数字小数点 → 算法对 `.!?` 要求前一字符是 CJK；`。！？` 直接匹配
- ✅ 锁死边界：保留 106-135（questionSources），仅删 146-184，仅在 197 行后插入
- ✅ `callLLM()`（SolvePipeline.kt:316-376）疑似 dead code，但仍传递 `references` —— 不动，本计划仅改 UI 渲染层
- ✅ 不加点击/链接交互
- ✅ 不加 Compose UI 测试基础设施，仅纯函数测试
- ✅ Q9 题号标注用 `s.questionSources` 过滤 `source != "题库匹配"` 得到 LLM 答题题号集合

---

## Work Objectives

### Core Objective
在 LLM 答题完成后，于悬浮侧边栏 Done 状态的答案下方追加一条 100~150 字的 Tavily 参考摘录（句末截断、HTML 清洗、题号自动标注），同时移除现有答案上方的多条标题+URL 引用区块。**复用现有 L3 搜索结果，零新增网络请求。**

### Concrete Deliverables
1. **新文件** `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`
   - `object ReferenceFormatter`
   - 公开方法 `formatSingleReference(references: List<Reference>, llmQuestionNumbers: List<Int>): String?`
   - 内部辅助 `internal fun stripHtml(input: String): String`
   - 内部辅助 `internal fun truncateToSentenceEnd(text: String, minLen: Int = 100, maxLen: Int = 150): String`
2. **修改** `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`
   - 删除第 146–184 行的旧引用块
   - 在 `ReworkButton`（原第 199 行）之前插入新单条参考的 `Text` 组件
3. **新文件** `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt`
   - 至少 8 个 `@Test`，覆盖空列表/全部太短/HTML 清洗/句末截断/fallback/单题/多题/无 LLM 题号
4. **质量门**: `./gradlew :app:testDebugUnitTest` 全绿

### Definition of Done
- [ ] `./gradlew :app:assembleDebug` 编译通过（PowerShell：`.\gradlew.bat :app:assembleDebug`）
- [ ] `./gradlew :app:testDebugUnitTest` 全部测试通过（包含新增的 `ReferenceFormatterTest`）
- [ ] `ReferenceFormatterTest` 至少 8 个测试用例，覆盖所有边界
- [ ] `SidebarStateRenderer.kt` 中已无第 146–184 行的旧多条引用块
- [ ] `SidebarStateRenderer.kt` 中第 197 行（原答案循环结束）后已新增单条参考 `Text`
- [ ] Final Wave 4 个并行审查全部 APPROVE
- [ ] 用户明确"okay"

### Must Have
- ReferenceFormatter 必须是纯函数（无 Android Context 依赖在公开 API 上；`Html.fromHtml` 是 Android framework 静态调用，可在单元测试中通过 Robolectric 或直接调用——但本项目无 Robolectric，因此**HTML 清洗必须用纯字符串/正则实现，不依赖 `android.text.Html`**，避免单元测试需要 Android 框架）
- 句末截断算法对 CJK 友好：`.!?` 仅在前一字符为 CJK 字符时视为句末
- HTML 清洗结果不含 `<...>` 标签、HTML 实体（`&nbsp;`、`&amp;`、`&lt;`、`&gt;`、`&quot;`）转换为对应字符或空格
- 复用 `SidebarState.Done.references`，**禁止新增 Tavily 调用**
- 新参考样式与现有 `bodySmall + TextSecondary` 模式一致

### Must NOT Have（Guardrails）
- ❌ 不修改 `SolvePipeline.kt`、`TavilyClient.kt`、`SearchManager.kt`、`LLMClient.kt`、`ExtractedTextBus.kt`、`AppConfig.kt`、`SettingsScreen.kt`、`Theme.kt`、`Type.kt`
- ❌ 不增加 Tavily 网络请求（包括 LLM 完成后再调一次的方案）
- ❌ 不增加新设置开关 / 新 DataStore key
- ❌ 不修改 4 级流水线判定逻辑
- ❌ 不修改无障碍服务、自动点击逻辑
- ❌ 不显示来源 URL / 域名 / 网站名 / 标题
- ❌ 不做 LLM 二次摘要
- ❌ 不引入新依赖（包括 Markdown 库、`androidx.test.*`、Robolectric、MockWebServer）
- ❌ 不在 `ReferenceFormatter` 公开 API 上依赖 `android.text.Html`（避免单元测试需要 Android framework）
- ❌ 不修改第 106–135 行 `questionSources` 显示
- ❌ 不给参考添加点击事件、长按、复制、链接跳转
- ❌ 不修改 `Reference` 数据类
- ❌ 不为新参考做独立的"加载中/失败"状态（静默即可）

---

## Verification Strategy（MANDATORY）

> **ZERO HUMAN INTERVENTION** — 所有验证由 Agent 执行。

### Test Decision
- **Infrastructure exists**: ✅ YES（JUnit 5 + MockK + Turbine）
- **Automated tests**: **Tests after**（B 选项）
- **Framework**: JUnit 5 (`org.junit.jupiter:junit-jupiter:5.11.0`)
- **Test 路径**: `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt`

### QA Policy
所有任务的 Agent QA 用以下工具：
- **代码静态校验**: `Bash` 执行 `.\gradlew.bat :app:compileDebugKotlin`
- **单元测试**: `Bash` 执行 `.\gradlew.bat :app:testDebugUnitTest`
- **编译产物**: `Bash` 执行 `.\gradlew.bat :app:assembleDebug`
- **代码内容验证**: `Read` + `Grep` 直接读取/搜索文件

证据保存到 `.omo/evidence/task-{N}-{scenario-slug}.{ext}`。

> **APK 安装到真机做端到端验证不在本计划范围内**——侧边栏需要无障碍权限+悬浮窗权限+真实 i 国网考试页面，无法在 CI/agent 环境中完整复现。可视化验证依赖编译通过 + 单元测试 + 代码 review。

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1（立即开始 — 基础工具 + 算法实现，3 个并行任务）:
├── 1. ReferenceFormatter 纯函数实现（util 包）[quick]
├── 2. SidebarStateRenderer 删除旧引用块（146–184）[quick]
└── 3. 现有代码地标确认（基线快照，编译基线）[quick]

Wave 2（依赖 Wave 1 — UI 接入 + 测试，2 个并行任务）:
├── 4. SidebarStateRenderer 新参考显示插入（依赖 1, 2）[visual-engineering]
└── 5. ReferenceFormatterTest 单元测试（依赖 1）[quick]

Wave 3（依赖 Wave 2 — 集成验证，1 个任务）:
└── 6. 全量编译 + 全量单元测试通过（依赖 4, 5）[quick]

Wave FINAL（依赖 Wave 3 — 4 个并行审查）:
├── F1. 计划合规审计（oracle）
├── F2. 代码质量审查（unspecified-high）
├── F3. 真实手动 QA — 编译/测试运行 + 视觉描述（unspecified-high）
└── F4. 范围保真度检查（deep）
→ 4 个全部 APPROVE 后向用户呈现结果，等待用户明确 "okay"

Critical Path: 1 → 4 → 6 → F1-F4 → user okay
Parallel Speedup: ~50%（Wave 1 三个并行 + Wave 2 两个并行）
Max Concurrent: 3（Wave 1）
```

### Dependency Matrix

| 任务 | 依赖 | 阻塞 |
|---|---|---|
| 1 | — | 4, 5 |
| 2 | — | 4 |
| 3 | — | 6（提供编译基线） |
| 4 | 1, 2 | 6 |
| 5 | 1 | 6 |
| 6 | 4, 5 | F1–F4 |
| F1 | 6 | user okay |
| F2 | 6 | user okay |
| F3 | 6 | user okay |
| F4 | 6 | user okay |

### Agent Dispatch Summary

- **Wave 1**: 3 任务 — 1 → `quick`，2 → `quick`，3 → `quick`
- **Wave 2**: 2 任务 — 4 → `visual-engineering`，5 → `quick`
- **Wave 3**: 1 任务 — 6 → `quick`
- **FINAL**: 4 任务 — F1 → `oracle`，F2 → `unspecified-high`，F3 → `unspecified-high`，F4 → `deep`

---

## TODOs

> Implementation + Test = ONE Task。每个任务必须含：Recommended Agent Profile + Parallelization + QA Scenarios。
> 任务编号格式严格使用 `1.`、`2.`、…、`6.`（bare number），Final Wave 用 `F1.`、`F2.`、`F3.`、`F4.`。

- [ ] 1. 创建 `ReferenceFormatter.kt` 纯函数工具类

  **What to do**:
  - 新建文件 `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`
  - 包名 `com.examhelper.app.util`
  - 导入 `com.examhelper.app.network.Reference`
  - 实现 `object ReferenceFormatter`，包含：
    1. **公开方法** `fun formatSingleReference(references: List<Reference>, llmQuestionNumbers: List<Int>): String?`
       - 遍历 references；对每条 ref：执行 `stripHtml(ref.snippet)` → `cleaned`；若 `cleaned.length >= 100`，进入截断；否则跳过
       - 用 `truncateToSentenceEnd(cleaned)` 截到 100~150 字范围
       - 拼接前缀：`llmQuestionNumbers` 为空 → `"🔍 参考: "`；非空 → `"🔍 参考（题 ${list.joinToString(", ")}）: "`
       - 返回拼接结果
       - 若全部 ref 都 < 100 字 → 返回 `null`
       - 若 `references` 为空 → 返回 `null`
    2. **internal `fun stripHtml(input: String): String`**
       - 实现：用正则 `Regex("<[^>]+>")` 移除标签
       - 替换 HTML 实体：`&nbsp;` → ` `（空格），`&amp;` → `&`，`&lt;` → `<`，`&gt;` → `>`，`&quot;` → `"`，`&#39;` → `'`
       - 折叠多余空白：`Regex("\\s+")` 替换为单空格
       - `.trim()`
       - **不依赖** `android.text.Html`（保持纯 JVM 可测）
    3. **internal `fun truncateToSentenceEnd(text: String, minLen: Int = 100, maxLen: Int = 150): String`**
       - 若 `text.length <= maxLen` → 返回 `text`
       - 在 `[minLen, maxLen]` 范围内反向查找句末位置：
         - 中文标点：`。！？` 直接命中
         - 英文标点：`.!?` 命中且**前一字符必须是 CJK 字符**（避免 `3.14` 误匹配）
         - CJK 字符判定：Unicode 区间 `0x4E00..0x9FFF`（基本汉字）或 `0x3400..0x4DBF`（扩展A）
       - 找到 → 返回 `text.substring(0, pos + 1)`（含标点）
       - 找不到 → 返回 `text.substring(0, maxLen) + "..."`

  **Must NOT do**:
  - ❌ 不依赖 `android.text.Html` 或任何 `android.*` 包（保持单元测试无需 Robolectric）
  - ❌ 不调用网络
  - ❌ 不依赖 `Context`、`Application`、`Resources`
  - ❌ 不修改 `Reference` 数据类
  - ❌ 不引入新依赖

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件新建，纯函数实现，无 UI 无网络，明确算法规则
  - **Skills**: 无
    - 该任务不需要 git-master / playwright / 其他特殊技能；标准代码生成即可
  - **Skills Evaluated but Omitted**:
    - `test-driven-development`: 测试任务在 5 单独承担，本任务先实现
    - `frontend-ui-ux`: 无 UI

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 2、3 并行）
  - **Blocks**: 4（UI 接入需要本类）、5（测试需要本类）
  - **Blocked By**: 无（可立即开始）

  **References**:

  **Pattern References**:
  - `app/src/main/java/com/examhelper/app/util/OptionTextUtils.kt` — 同包路径下纯字符串工具的命名/结构模式参考
  - `app/src/main/java/com/examhelper/app/util/AccessibilityParseUtils.kt` — `object` + 多个 `fun` 的工具类模式

  **API/Type References**:
  - `app/src/main/java/com/examhelper/app/network/TavilyClient.kt:78-82` — `Reference(title: String, url: String, snippet: String)` 数据类（要 import）
  - `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt:32` — `SidebarState.Done.references: List<Reference>` 字段，确认数据流来源

  **External References**:
  - Kotlin Regex docs: `Regex("<[^>]+>")` 标准 HTML 标签匹配
  - Unicode CJK 区间：`0x4E00..0x9FFF`（CJK Unified Ideographs，覆盖绝大多数中文字符）

  **WHY Each Reference Matters**:
  - `OptionTextUtils.kt`：本项目 `util` 包下 `object` 工具类的代码风格基线，新文件应保持一致
  - `TavilyClient.kt:78-82`：`Reference` 是公开数据类的源头，本任务的输入来自这里——必须 import
  - `ExtractedTextBus.kt:32`：调用方 `SidebarStateRenderer.kt` 会从 `Done.references` 取数据传入本工具，确认契约一致

  **Acceptance Criteria**:

  - [ ] 文件 `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt` 存在
  - [ ] `Test-Path "C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\util\ReferenceFormatter.kt"` → `True`
  - [ ] `Select-String -Path <ReferenceFormatter.kt> -Pattern "object ReferenceFormatter"` → 至少 1 命中
  - [ ] `Select-String -Path <ReferenceFormatter.kt> -Pattern "fun formatSingleReference"` → 至少 1 命中
  - [ ] `Select-String -Path <ReferenceFormatter.kt> -Pattern "internal fun stripHtml"` → 至少 1 命中
  - [ ] `Select-String -Path <ReferenceFormatter.kt> -Pattern "internal fun truncateToSentenceEnd"` → 至少 1 命中
  - [ ] `Select-String -Path <ReferenceFormatter.kt> -Pattern "android\\.text\\.Html"` → **0 命中**（确认不依赖 Android Html）
  - [ ] `.\gradlew.bat :app:compileDebugKotlin` 通过

  **QA Scenarios**:

  ```
  Scenario: ReferenceFormatter 编译通过且符合 API 契约
    Tool: Bash (PowerShell)
    Preconditions: 文件已创建，import 正确
    Steps:
      1. 执行 `.\gradlew.bat :app:compileDebugKotlin` (workdir=C:\Users\lasal\i_exam)
      2. 输出包含 "BUILD SUCCESSFUL"
      3. 执行 `Select-String -Path "C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\util\ReferenceFormatter.kt" -Pattern "fun formatSingleReference\(references: List<Reference>, llmQuestionNumbers: List<Int>\): String\\?"`
      4. 验证 ≥1 命中（公开 API 签名正确）
    Expected Result: 编译成功，公开 API 签名匹配规约
    Failure Indicators: BUILD FAILED；签名不匹配；找不到文件
    Evidence: .omo/evidence/task-1-compile-and-api.txt

  Scenario: ReferenceFormatter 不依赖 Android framework（保持单测可在 JVM 跑）
    Tool: Bash (PowerShell)
    Preconditions: 文件已创建
    Steps:
      1. 执行 `Select-String -Path "C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\util\ReferenceFormatter.kt" -Pattern "android\\."`
      2. 验证 0 命中
    Expected Result: 0 命中
    Failure Indicators: 任何 `android.` 包导入或引用
    Evidence: .omo/evidence/task-1-no-android-deps.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-1-compile-and-api.txt` (gradle 输出 + grep 结果)
  - [ ] `.omo/evidence/task-1-no-android-deps.txt` (grep 结果)

  **Commit**: NO（与其他任务一起在 Final Wave 后单次提交）

- [ ] 2. 删除 `SidebarStateRenderer.kt` 中旧的多条引用块（146–184 行）

  **What to do**:
  - 打开 `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`
  - 定位现有代码块：从 `if (s.references.isNotEmpty()) {` 开始（约第 146 行）到对应的 `}` + `Spacer(Modifier.height(8.dp))` 结束（约第 184 行）
  - 该块包含：`SectionHeader("🔍 参考资料...")` + `Column { s.references.take(5).forEachIndexed { ... } }` + `Spacer`
  - **完整删除**该 if 块（含起始的 `Spacer(Modifier.height(8.dp))` 和结束的 `Spacer(Modifier.height(8.dp))`）
  - 同时删除该块上方/内部仅服务于该块的局部变量（如 `llmQuestions` 临时变量，**当且仅当**它仅在删除范围内使用——若 questionSources 显示也用了 `llmQuestions`，则保留）
  - **保留**第 106–135 行的 `questionSources` 显示（"📋 题库匹配 / 🤖 AI模型"标注）
  - **保留**第 185–197 行的答案逐行渲染
  - **保留**第 199 行的 `ReworkButton`
  - 删除后保持文件 Compose 语法合法（balanced braces、正确的 Column 嵌套）

  **Must NOT do**:
  - ❌ 不动第 100-145 行（包括 questionSources 显示）
  - ❌ 不动第 185-197 行（答案渲染）
  - ❌ 不动第 199 行后的代码（ReworkButton、SaveToKBButton）
  - ❌ 不动其他 state 分支（Idle/Loading/Preview/Streaming/Error）
  - ❌ 不删除 import（保留 `Reference` 等其他 import；任务 4 会重新使用 `s.references`）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单点删除操作，明确行号范围
  - **Skills**: 无
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: 不是设计任务，是机械删除

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 1、3 并行）
  - **Blocks**: 4（UI 接入要在已删除旧块的基础上插入新块）
  - **Blocked By**: 无

  **References**:

  **Pattern References**:
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt:146-184` — 待删除的现有引用块（含 SectionHeader + Column + 嵌套 Row + 内部 Text 渲染）
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt:106-135` — 必须保留的 questionSources 显示
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt:185-197` — 必须保留的答案 forEach 渲染
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt:199` — 必须保留的 ReworkButton

  **WHY Each Reference Matters**:
  - 146-184：删除目标必须精确，过度删除会破坏 Compose 语法
  - 106-135 / 185-197 / 199：边界证据，编辑前后用 grep 确认这些行未被改动

  **Acceptance Criteria**:

  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "🔍 参考资料"` → 0 命中
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "s\\.references\\.take\\(5\\)"` → 0 命中
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "📋 题库匹配"` → 仍然 ≥1 命中（questionSources 块未动）
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "🤖 AI模型"` → 仍然 ≥1 命中
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "ReworkButton"` → 仍然 ≥1 命中
  - [ ] `.\gradlew.bat :app:compileDebugKotlin` 通过（语法平衡）

  **QA Scenarios**:

  ```
  Scenario: 旧多条引用块已完整删除，questionSources 与 ReworkButton 完好
    Tool: Bash (PowerShell)
    Preconditions: 已对 SidebarStateRenderer.kt 完成编辑
    Steps:
      1. 执行：
         $f="C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\ui\sidebar\SidebarStateRenderer.kt"
         Select-String -Path $f -Pattern "🔍 参考资料"
         Select-String -Path $f -Pattern "📋 题库匹配"
         Select-String -Path $f -Pattern "ReworkButton"
      2. 第 1 个为 0 行；第 2、3 个均 ≥1 行
      3. 执行 `.\gradlew.bat :app:compileDebugKotlin` (workdir=C:\Users\lasal\i_exam)
      4. 输出 "BUILD SUCCESSFUL"
    Expected Result: 删除点匹配 0；保留点匹配 ≥1；编译成功
    Failure Indicators: 删除点仍有命中；保留点丢失；编译失败
    Evidence: .omo/evidence/task-2-deletion-and-compile.txt

  Scenario: 边界保留验证（边界行未被误删）
    Tool: Bash (PowerShell)
    Preconditions: 已编辑
    Steps:
      1. 读取文件，确认仍包含答案行渲染逻辑：
         Select-String -Path $f -Pattern "s\\.answer\\.lines\\(\\)"
      2. ≥1 命中
    Expected Result: 答案 forEach 仍存在
    Evidence: .omo/evidence/task-2-boundary-preserved.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-2-deletion-and-compile.txt`
  - [ ] `.omo/evidence/task-2-boundary-preserved.txt`

  **Commit**: NO

- [ ] 3. 基线确认：编译通过 + 关键代码地标快照

  **What to do**:
  - 在所有改动前，执行一次 `.\gradlew.bat :app:compileDebugKotlin` 与 `.\gradlew.bat :app:testDebugUnitTest`，记录基线状态
  - 用 `Read` 抓取以下文件的当前关键行作为快照（写到证据文件）：
    - `SidebarStateRenderer.kt` 的当前总行数（用 PowerShell `(Get-Content -LiteralPath $f).Count`）
    - `SidebarStateRenderer.kt` 第 100-205 行内容
    - `ExtractedTextBus.kt` 第 26-40 行（确认 SidebarState.Done 字段）
    - `TavilyClient.kt` 第 75-90 行（确认 Reference 数据类）
    - `Theme.kt` 第 14-18 行（确认 TextSecondary 色值）
  - **本任务作为基线，不修改任何代码**
  - 该任务为 Wave 3 的"无回归"提供对照基础

  **Must NOT do**:
  - ❌ 不修改任何代码
  - ❌ 不安装新工具
  - ❌ 不动 `.gitignore`、`local.properties`、`gradle.properties`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 只读快照 + 命令执行
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1（与 1、2 并行）
  - **Blocks**: 6（提供基线对照）
  - **Blocked By**: 无

  **References**:

  **Pattern References**:
  - `C:\Users\lasal\i_exam\PERFORMANCE_REPORT.md` — 项目已有的快照式文档可作格式参考

  **WHY Each Reference Matters**:
  - 该任务产物是给 F1/F4 审查者用的对照证据，必须是机械可读的纯文本

  **Acceptance Criteria**:

  - [ ] `.omo/evidence/task-3-baseline-compile.txt` 包含 "BUILD SUCCESSFUL"
  - [ ] `.omo/evidence/task-3-baseline-tests.txt` 包含测试统计行（pass/fail count）
  - [ ] `.omo/evidence/task-3-baseline-snapshots.md` 包含 5 个文件的关键行内容

  **QA Scenarios**:

  ```
  Scenario: 基线编译与测试干净
    Tool: Bash (PowerShell)
    Preconditions: 仓库未被任何任务改动
    Steps:
      1. 执行 `.\gradlew.bat :app:compileDebugKotlin` (workdir=C:\Users\lasal\i_exam)，stdout 写入 task-3-baseline-compile.txt
      2. 执行 `.\gradlew.bat :app:testDebugUnitTest` (workdir=C:\Users\lasal\i_exam)，stdout 写入 task-3-baseline-tests.txt
      3. 两次执行均含 "BUILD SUCCESSFUL"
    Expected Result: 基线干净通过
    Failure Indicators: BUILD FAILED 或测试 fail（说明本计划开始前仓库已损坏，需先修复）
    Evidence: .omo/evidence/task-3-baseline-compile.txt, .omo/evidence/task-3-baseline-tests.txt

  Scenario: 关键文件快照已抓取
  Tool: Bash (PowerShell)
    Steps:
      1. 写入 `.omo/evidence/task-3-baseline-snapshots.md` 含 5 个文件的关键行
      2. 文件存在且非空
    Expected Result: 快照文件就绪
    Evidence: .omo/evidence/task-3-baseline-snapshots.md
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-3-baseline-compile.txt`
  - [ ] `.omo/evidence/task-3-baseline-tests.txt`
  - [ ] `.omo/evidence/task-3-baseline-snapshots.md`

  **Commit**: NO（不产生代码变更）

- [ ] 4. 在 `SidebarStateRenderer.kt` 中插入新的单条参考显示

  **What to do**:
  - 打开 `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`
  - 在 import 区添加：`import com.examhelper.app.util.ReferenceFormatter`
  - 在原第 197 行（答案 `Text` 循环 `lines.forEach { ... }` 结束的右大括号）之后、原第 199 行（`ReworkButton` 调用）之前，插入以下代码块：

    ```kotlin
    // === Tavily 单条参考摘录（100~150 字，句末截断，自动标注 AI 答题题号） ===
    val llmQuestionNumbers = s.questionSources
        .filter { (_, source) -> source != "题库匹配" }
        .keys
        .sorted()
        .toList()
    ReferenceFormatter.formatSingleReference(s.references, llmQuestionNumbers)?.let { refText ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = refText,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
            lineHeight = 18.sp
        )
    }
    ```
  - 确保 `TextSecondary` 已 import（来自 `com.examhelper.app.ui.theme.TextSecondary`）—— 若未 import 则添加
  - 该代码必须放在 Done 状态分支的 Column 作用域内，**不能**插到 if 块外或其他 state 分支
  - 确认编译通过、Compose 语法平衡

  **Must NOT do**:
  - ❌ 不在该 `Text` 上添加 `Modifier.clickable` / `pointerInput` / 长按事件
  - ❌ 不显示 URL、网站名、来源标识
  - ❌ 不单独捕获异常（`ReferenceFormatter` 是纯函数，已保证不抛异常）
  - ❌ 不修改 `s.questionSources` 的判定逻辑（仅做 `source != "题库匹配"` 过滤；保持 i18n 字面值与项目其他位置一致）
  - ❌ 不改变 Done 分支其他元素的顺序或样式
  - ❌ 不修改本任务范围外的其他 state 分支

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose UI 微调，涉及组件嵌入位置、样式 token、与现有视觉风格的连续性
  - **Skills**: 无（项目无 frontend-design system 文档；样式 token 已在 Theme.kt）
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: 不需要重新设计，仅复用现有 token

  **Parallelization**:
  - **Can Run In Parallel**: NO（强依赖前序任务）
  - **Parallel Group**: Wave 2（与 5 并行）
  - **Blocks**: 6
  - **Blocked By**: 1（需要 ReferenceFormatter 类）、2（需要旧块已被删除以确定插入锚点）

  **References**:

  **Pattern References**:
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt:185-197` — 答案行 `Text` 渲染（插入位置紧跟其后）；样式参考其使用的 `style = MaterialTheme.typography.bodyMedium` 模式
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt:106-135` — questionSources 显示，从中确认 `source != "题库匹配"` 这条字面值的正确性
  - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt` 内已使用 `MaterialTheme.typography.bodySmall` 的位置 — 风格一致

  **API/Type References**:
  - `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`（任务 1 产出）— 公开 API
  - `app/src/main/java/com/examhelper/app/ui/theme/Theme.kt:16` — `TextSecondary = Color(0xFF9CA3AF)`
  - `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt:32` — `SidebarState.Done.questionSources: Map<Int, String>`、`references: List<Reference>`

  **WHY Each Reference Matters**:
  - 185-197：插入锚点必须紧贴答案循环之后；如果插错位置可能落到 `Streaming` 分支或外层
  - 106-135：`"题库匹配"` 这个字面值必须与现有 questionSources 写入端一致（搜代码确认）
  - Theme.kt:16：色值 token，必须复用而非新定义

  **Acceptance Criteria**:

  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "ReferenceFormatter"` → ≥2 命中（import + 调用）
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "llmQuestionNumbers"` → ≥1 命中
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "TextSecondary"` → ≥1 命中
  - [ ] `Select-String -Path <SidebarStateRenderer.kt> -Pattern "Modifier\\.clickable"` 在新参考代码段附近 0 命中（用上下文 grep）
  - [ ] `.\gradlew.bat :app:compileDebugKotlin` 通过
  - [ ] `.\gradlew.bat :app:assembleDebug` 通过

  **QA Scenarios**:

  ```
  Scenario: 新参考插入点正确，编译/构建通过
    Tool: Bash (PowerShell)
    Preconditions: 任务 1、2 已完成
    Steps:
      1. 执行 `.\gradlew.bat :app:compileDebugKotlin` (workdir=C:\Users\lasal\i_exam) → "BUILD SUCCESSFUL"
      2. 执行 `.\gradlew.bat :app:assembleDebug` (workdir=C:\Users\lasal\i_exam) → "BUILD SUCCESSFUL"
      3. 验证 import 已添加：
         Select-String -Path <SidebarStateRenderer.kt> -Pattern "import com\\.examhelper\\.app\\.util\\.ReferenceFormatter"
         → ≥1 命中
      4. 验证调用形式正确：
         Select-String -Path <SidebarStateRenderer.kt> -Pattern "ReferenceFormatter\\.formatSingleReference"
         → ≥1 命中
    Expected Result: 编译/构建均通过；import 与调用均存在
    Failure Indicators: 编译失败、import 缺失、放错位置
    Evidence: .omo/evidence/task-4-compile-and-build.txt

  Scenario: 新参考无交互（无点击/长按）
    Tool: Bash (PowerShell)
    Steps:
      1. 用 awk/Select-String 提取从 "ReferenceFormatter.formatSingleReference" 到下一个 `}` 的代码片段
      2. 在该片段内 grep `clickable|pointerInput|combinedClickable` → 0 命中
    Expected Result: 0 命中
    Failure Indicators: 任何点击/长按 modifier
    Evidence: .omo/evidence/task-4-no-interaction.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-4-compile-and-build.txt`
  - [ ] `.omo/evidence/task-4-no-interaction.txt`

  **Commit**: NO

- [ ] 5. 创建 `ReferenceFormatterTest.kt` 单元测试

  **What to do**:
  - 新建文件 `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt`
  - 使用 JUnit 5（`org.junit.jupiter.api.Test`、`Assertions.*`）
  - 至少 8 个 `@Test`，覆盖以下边界：

    1. **`空引用列表返回 null`** — `formatSingleReference(emptyList(), emptyList())` → null
    2. **`所有 snippet 短于 100 字时返回 null`** — 3 条 ref，snippet 分别 50/80/95 字 → null
    3. **`首条 snippet 长于 150 字时按句末截断`** — snippet = 200 字含中文句号在 110/130/180 位置 → 截到 130 位置（最后一个 `[100,150]` 范围内的 `。`）
    4. **`首条 snippet 在 100~150 字之间时直接返回`** — snippet = 120 字（无截断需要）→ 完整返回（含前缀）
    5. **`首条太短跳过到第二条`** — refs[0].snippet = 50 字、refs[1].snippet = 200 字 → 用第二条做截断
    6. **`HTML 标签清洗`** — snippet 含 `<br>`、`<b>...</b>`、`&nbsp;`、`&amp;` → 输出无标签且实体已转义
    7. **`英文小数点不被误判为句末`** — snippet 含 `经测试3.14mg/kg符合标准` → 截断不切在 `3` 后
    8. **`无任何句末标点时 fallback take(150) + "..."`** — snippet = 200 字英文无标点 → 输出长度 = 153（150 字符 + 3 字 `...`），且**前缀加在最前面**
    9. **`单 LLM 题号显示前缀`** — `llmQuestionNumbers = [3]` → 输出以 `🔍 参考（题 3）: ` 开头
    10. **`多 LLM 题号显示前缀`** — `llmQuestionNumbers = [3, 4, 5]` → 输出以 `🔍 参考（题 3, 4, 5）: ` 开头
    11. **`无 LLM 题号时显示通用前缀`** — `llmQuestionNumbers = emptyList()` → 输出以 `🔍 参考: ` 开头

  - 测试命名遵循项目约定（参考 `SearchManagerTest.kt`）：使用反引号包裹的中文/英文混排名称
  - 不使用 MockK / Turbine（纯函数测试）

  **Must NOT do**:
  - ❌ 不依赖 Android framework（Robolectric / `androidx.test.*`）
  - ❌ 不引入新依赖
  - ❌ 不测 Compose UI（不在范围内）
  - ❌ 不依赖网络
  - ❌ 不用 `runBlocking` / `kotlinx-coroutines-test`（被测函数非 suspend）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准 JUnit 5 测试，无 mock，无 Flow，断言直接
  - **Skills**: `test-driven-development`
    - Reason: 虽然是 tests-after，但 TDD 红绿循环规则可指导每个 case 写最小断言
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: 无 UI

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 4 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: 6
  - **Blocked By**: 1（需要被测对象 ReferenceFormatter）

  **References**:

  **Pattern References**:
  - `app/src/test/java/com/examhelper/app/SearchManagerTest.kt` — 项目内 JUnit 5 测试的命名/组织/断言风格基线（**重要**：复刻其代码风格）
  - `app/src/test/java/com/examhelper/app/AccessibilityParseUtilsTest.kt` — 纯字符串处理工具的测试模式（最贴近本任务）
  - `app/src/test/java/com/examhelper/app/KBEntryTest.kt` — 同样测试纯函数的样板

  **API/Type References**:
  - `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`（任务 1 产出）
  - `app/src/main/java/com/examhelper/app/network/TavilyClient.kt:78-82` — `Reference(title, url, snippet)`（构造测试 fixture 用）
  - `app/build.gradle.kts:100-102` — JUnit 5 + MockK + Turbine 已配置；本任务只用 JUnit 5

  **WHY Each Reference Matters**:
  - `AccessibilityParseUtilsTest.kt`：是本项目里**最贴近本任务**的测试样板（纯字符串处理 + 多个 case），新测试代码风格、import 顺序、断言写法应严格复刻
  - `Reference` 数据类：测试 fixture 必须用真实数据类构造，不能 mock

  **Acceptance Criteria**:

  - [ ] 文件 `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt` 存在
  - [ ] `Select-String -Path <ReferenceFormatterTest.kt> -Pattern "@Test"` → ≥8 命中（不少于 8 个测试用例）
  - [ ] `Select-String -Path <ReferenceFormatterTest.kt> -Pattern "import org\\.junit\\.jupiter\\.api"` → ≥1 命中
  - [ ] `Select-String -Path <ReferenceFormatterTest.kt> -Pattern "Robolectric"` → 0 命中
  - [ ] `.\gradlew.bat :app:testDebugUnitTest --tests "com.examhelper.app.util.ReferenceFormatterTest"` 通过

  **QA Scenarios**:

  ```
  Scenario: ReferenceFormatterTest 全绿
    Tool: Bash (PowerShell)
    Preconditions: 任务 1 已完成（被测代码存在）
    Steps:
      1. 执行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.examhelper.app.util.ReferenceFormatterTest"` (workdir=C:\Users\lasal\i_exam)，stdout 写入 task-5-test-output.txt
      2. 输出包含 "BUILD SUCCESSFUL"
      3. 输出含 "tests successful" 或类似成功统计
    Expected Result: 全部测试通过
    Failure Indicators: BUILD FAILED；任何 test failed
    Evidence: .omo/evidence/task-5-test-output.txt

  Scenario: 测试用例数量与覆盖范围达标
    Tool: Bash (PowerShell)
    Steps:
      1. `(Select-String -Path <ReferenceFormatterTest.kt> -Pattern "@Test").Count` → ≥8
      2. 用 grep 抽查关键 case 名（中文或英文 keyword）：
         - 空列表/empty
         - 太短/short
         - HTML / `<br>`
         - 小数点/3\\.14
         - fallback / `\\.\\.\\.`
         - 题号/题
      3. 每个关键词至少出现 1 次
    Expected Result: 用例数 ≥8 且覆盖关键边界
    Evidence: .omo/evidence/task-5-coverage-grep.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-5-test-output.txt`
  - [ ] `.omo/evidence/task-5-coverage-grep.txt`

  **Commit**: NO

- [ ] 6. 全量编译 + 全量单元测试 + 与基线对照

  **What to do**:
  - 在所有实现任务（1、2、4、5）完成后，执行：
    1. `.\gradlew.bat :app:compileDebugKotlin` (workdir=C:\Users\lasal\i_exam)
    2. `.\gradlew.bat :app:testDebugUnitTest` (workdir=C:\Users\lasal\i_exam)
    3. `.\gradlew.bat :app:assembleDebug` (workdir=C:\Users\lasal\i_exam)
  - 三个命令的 stdout 全部写入证据文件
  - **回归对比**：
    - 与任务 3 抓取的 `task-3-baseline-tests.txt` 对比，确认：
      - 原有测试用例数（应该 = 基线 + 新增 ≥8）
      - 原有测试用例**全部仍通过**（无 regression）
      - 新增的 `ReferenceFormatterTest` ≥8 个用例均通过
  - 写一份 `task-6-summary.md`，列出：
    - 改动的文件清单（与计划匹配）
    - 测试统计（基线 vs 新）
    - 编译/构建状态
    - 与计划"Must Have / Must NOT Have"逐条核对结果

  **Must NOT do**:
  - ❌ 不修改代码（仅运行命令并对照结果）
  - ❌ 不安装新工具
  - ❌ 不忽略任何测试失败
  - ❌ 不提交代码（提交在 Final Wave 用户 okay 之后）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 命令执行 + 报告生成
  - **Skills**: 无
  - **Skills Evaluated but Omitted**:
    - `verification-before-completion`: 概念上对应，但本任务流程已在 What to do 显式列出

  **Parallelization**:
  - **Can Run In Parallel**: NO（必须等所有实现任务完成）
  - **Parallel Group**: Wave 3（独立一波）
  - **Blocks**: F1、F2、F3、F4
  - **Blocked By**: 4、5

  **References**:

  **Pattern References**:
  - `C:\Users\lasal\i_exam\PERFORMANCE_REPORT.md` — 项目报告写法（清单 + 数据 + 结论）

  **External References**:
  - Gradle CLI: `:app:testDebugUnitTest`、`:app:compileDebugKotlin`、`:app:assembleDebug` 是项目唯一支持的命令组合（来自 `app/build.gradle.kts`）

  **WHY Each Reference Matters**:
  - 报告样式必须利于 F1/F4 审查者机械读取

  **Acceptance Criteria**:

  - [ ] `.\gradlew.bat :app:compileDebugKotlin` "BUILD SUCCESSFUL"
  - [ ] `.\gradlew.bat :app:testDebugUnitTest` "BUILD SUCCESSFUL"，所有原测试 + 新测试全部通过
  - [ ] `.\gradlew.bat :app:assembleDebug` "BUILD SUCCESSFUL"，产出 `app/build/outputs/apk/debug/app-debug.apk`
  - [ ] 任务 5 创建的 `ReferenceFormatterTest` 用例数（@Test 数量）≥ 8
  - [ ] 与基线对比：原有测试**无任何 regression**（pass/fail count 一致或更好）
  - [ ] `.omo/evidence/task-6-summary.md` 含改动清单 + 测试统计 + 编译状态 + Must Have/Must NOT 核对

  **QA Scenarios**:

  ```
  Scenario: 全量绿灯
    Tool: Bash (PowerShell)
    Preconditions: 任务 1, 2, 4, 5 全部完成
    Steps:
      1. 执行 `.\gradlew.bat :app:compileDebugKotlin` (workdir=C:\Users\lasal\i_exam) → stdout → task-6-compile.txt
      2. 执行 `.\gradlew.bat :app:testDebugUnitTest` (workdir=C:\Users\lasal\i_exam) → stdout → task-6-tests.txt
      3. 执行 `.\gradlew.bat :app:assembleDebug` (workdir=C:\Users\lasal\i_exam) → stdout → task-6-assemble.txt
      4. 三个文件均含 "BUILD SUCCESSFUL"
      5. `Test-Path "C:\Users\lasal\i_exam\app\build\outputs\apk\debug\app-debug.apk"` → True
    Expected Result: 全绿且 APK 存在
    Failure Indicators: 任何一步 BUILD FAILED；APK 缺失；旧测试 regression
    Evidence: .omo/evidence/task-6-compile.txt, .omo/evidence/task-6-tests.txt, .omo/evidence/task-6-assemble.txt

  Scenario: 与基线对照无回归
    Tool: Bash (PowerShell)
    Steps:
      1. 读 `.omo/evidence/task-3-baseline-tests.txt` 提取测试统计行
      2. 读 `.omo/evidence/task-6-tests.txt` 提取测试统计行
      3. 验证：新 - 基线 ≥ 8（新增 ReferenceFormatterTest 用例）
      4. 验证：新的 fail count = 0（无 regression）
      5. 写入 `task-6-summary.md` 含完整对照表
    Expected Result: 无回归，测试用例数增加 ≥8
    Failure Indicators: 任何旧测试 fail；新增数量不达 8
    Evidence: .omo/evidence/task-6-summary.md

  Scenario: 改动文件清单合规
    Tool: Bash (PowerShell)
    Steps:
      1. 执行 `git -C C:\Users\lasal\i_exam status --porcelain` → stdout → task-6-git-status.txt
      2. 验证仅以下文件改动：
         - `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`（新增）
         - `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt`（新增）
         - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`（修改）
         - `.omo/evidence/...`（证据，可有可无）
         - `.omo/plans/...` / `.omo/drafts/...`（可有可无）
      3. **不允许**其他文件出现在 git status（特别是 SolvePipeline.kt、TavilyClient.kt、AppConfig.kt、SettingsScreen.kt、build.gradle.kts）
    Expected Result: 改动清单与计划完全一致
    Failure Indicators: 任何范围外的文件被改动
    Evidence: .omo/evidence/task-6-git-status.txt
  ```

  **Evidence to Capture**:
  - [ ] `.omo/evidence/task-6-compile.txt`
  - [ ] `.omo/evidence/task-6-tests.txt`
  - [ ] `.omo/evidence/task-6-assemble.txt`
  - [ ] `.omo/evidence/task-6-summary.md`
  - [ ] `.omo/evidence/task-6-git-status.txt`

  **Commit**: NO（**唯一一次提交在 Final Wave 全部 APPROVE 且用户 okay 之后**）

---

## Final Verification Wave（在所有实现任务完成后必跑）

> 4 个审查 Agent 并行运行，全部必须 APPROVE。Final Wave 任务完成后，向用户呈现合并结果，并**等待用户明确"okay"**才能完成。

- [ ] F1. **Plan Compliance Audit** — `oracle`

  端到端读 plan。逐项验证：① 每条 "Must Have" 在代码中存在（Read/Grep 确认）；② 每条 "Must NOT Have" 不存在（Grep 搜禁止模式，含文件:行 引用）；③ 6 个实现任务全部完成；④ 证据文件存在于 `.omo/evidence/`。比较交付物与计划。
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`

  执行 `.\gradlew.bat :app:compileDebugKotlin`、`.\gradlew.bat :app:testDebugUnitTest`、`.\gradlew.bat :app:assembleDebug`。审查所有改动文件：禁止 `@Suppress`、空 catch、调试日志（`Log.d` 调试用 / `println`）、注释掉的代码、未使用的 import。检查 AI slop：过度注释、无效抽象、泛型命名（`data`、`result`、`item`、`temp`）。
  Output: `Compile [PASS/FAIL] | Tests [N pass/N fail] | Assemble [PASS/FAIL] | Files [N clean/N issues] | VERDICT: APPROVE/REJECT`

- [ ] F3. **Real Manual QA** — `unspecified-high`

  从干净状态开始。执行所有 QA 场景。本项目无法在 agent 环境跑端到端 UI（需真机+无障碍+i国网），因此 QA 重点为：
  - 跑全量单元测试（`.\gradlew.bat :app:testDebugUnitTest`）→ 抓 stdout 到 `.omo/evidence/final-qa/test-output.txt`
  - 跑构建（`.\gradlew.bat :app:assembleDebug`）→ 抓 stdout 到 `.omo/evidence/final-qa/build-output.txt`
  - 模拟 4 种 `SidebarState.Done` 输入（多题 L1+L4、单题 L4、L1 only、references 全空），写一个临时 Kotlin 脚本调 `ReferenceFormatter.formatSingleReference(...)` 验证输出（或者扩展 `ReferenceFormatterTest` 加这 4 个集成场景测试）
  - 输出 `task-F3-functional-coverage.md` 描述每种场景的实际输出
  Output: `Scenarios [N/N pass] | Tests [N/N] | Build [PASS/FAIL] | VERDICT: APPROVE/REJECT`

- [ ] F4. **Scope Fidelity Check** — `deep`

  对每个任务：读 "What to do"，读实际 git diff（`git -C C:\Users\lasal\i_exam diff`）。1:1 验证——计划中的全部已实现（无遗漏）；计划外的全部未实现（无超范围）。检查 Must NOT Have 合规。检查跨任务污染（任务 N 改了任务 M 的文件）。检查未声明的改动（修改了非计划文件）。
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT: APPROVE/REJECT`

---

## Commit Strategy

单次提交，不拆分（改动量小、原子性强）：

- **Commit 1**（实现完成 + 测试通过后，**Final Wave 全部 APPROVE 且用户 okay 之后**）:
  - Message: `feat(sidebar): 答案下方追加单条 Tavily 参考摘录（100~150字）`
  - Files:
    - `app/src/main/java/com/examhelper/app/util/ReferenceFormatter.kt`（新增）
    - `app/src/test/java/com/examhelper/app/util/ReferenceFormatterTest.kt`（新增）
    - `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`（修改）
  - Pre-commit: `.\gradlew.bat :app:testDebugUnitTest` 通过

> **注意**: 仅在用户明确 okay 之后提交。

---

## Success Criteria

### Verification Commands

```powershell
# 编译检查
.\gradlew.bat :app:compileDebugKotlin
# 期望：BUILD SUCCESSFUL

# 单元测试
.\gradlew.bat :app:testDebugUnitTest
# 期望：BUILD SUCCESSFUL，所有测试 pass，包含至少 8 个 ReferenceFormatterTest 用例

# 构建产物
.\gradlew.bat :app:assembleDebug
# 期望：BUILD SUCCESSFUL，产出 app/build/outputs/apk/debug/app-debug.apk

# 验证旧引用块已删除（应输出 0 行）
Select-String -Path "C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\ui\sidebar\SidebarStateRenderer.kt" -Pattern "🔍 参考资料"
# 期望：无输出（旧 SectionHeader 已删除）

# 验证新参考代码已添加（应至少 1 处）
Select-String -Path "C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\ui\sidebar\SidebarStateRenderer.kt" -Pattern "ReferenceFormatter"
# 期望：至少 1 个匹配（import 或调用）

# 验证 ReferenceFormatter 已创建
Test-Path "C:\Users\lasal\i_exam\app\src\main\java\com\examhelper\app\util\ReferenceFormatter.kt"
# 期望：True

# 验证测试已创建
Test-Path "C:\Users\lasal\i_exam\app\src\test\java\com\examhelper\app\util\ReferenceFormatterTest.kt"
# 期望：True

# 禁止新增依赖（应输出 0 行）
git -C C:\Users\lasal\i_exam diff app/build.gradle.kts
# 期望：无输出（build.gradle.kts 不动）
```

### Final Checklist
- [ ] 全部 "Must Have" 已实现
- [ ] 全部 "Must NOT Have" 已确认不存在
- [ ] `.\gradlew.bat :app:testDebugUnitTest` 全绿
- [ ] `.\gradlew.bat :app:assembleDebug` 编译通过
- [ ] 全部证据文件存在于 `.omo/evidence/`
- [ ] Final Wave F1-F4 全部 APPROVE
- [ ] 用户明确 "okay"
