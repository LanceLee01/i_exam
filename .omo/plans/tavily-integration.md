# Tavily 云搜索集成到 ExamHelper

## TL;DR

> **Quick Summary**: 在现有答题流程的 L2（Wiki 知识库）与 L3（LLM）之间插入 Tavily 联网搜索层。Android 端通过 OkHttp 直调 Tavily API，搜索结果作为上下文注入 LLM，最终显示带引用的答案。
>
> **Deliverables**:
> - `TavilyClient.kt` — Tavily API 的 OkHttp 客户端（search 方法）
> - `SearchManager.kt` — 搜索管理层（关键词提取 + 调度 + 结果归并）
> - `AppConfig.kt` 扩展 — 新增 `tavilyApiKey` 配置项
> - `ExtractedTextBus.kt` 扩展 — 新增 `AnswerSource.SEARCH_MATCH` + `Done` 增加 `references` 字段
> - `SolvePipeline.kt` 修改 — 插入搜索层 + 修复 `CancellationException` bug
> - `SettingsScreen.kt` 修改 — 新增 Tavily API Key 输入卡片
> - `SidebarPanel.kt` 修改 — Done 状态展示引用链接
>
> **Estimated Effort**: Short (~1天)
> **Parallel Execution**: YES — 3 waves (W1: 2, W2: 4, W3: 2)
> **Critical Path**: Task 1 → Task 3 → Task 5 → Task 7 → F1-F4

---

## Context

### Original Request
用户基于设计文档 `exam-helper-tavily-cloud-integration.md`，要求在现有 ExamHelper Android 应用中将 Tavily 云搜索 API 集成到答题流程中。

### Interview Summary
**Key Discussions**:
- 用户提供了详细的设计文档（619行），包含架构图、代码片段、集成步骤
- 用户只说了"继续"，未回答具体选择性问题，使用合理默认值推进

**Research Findings**（来自 Metis 差距分析）:
- 项目已有 OkHttp 4.12.0、Gson 2.11.0、Kotlin Coroutines 1.8.1 → **无需新增 HTTP 依赖**
- AppConfig 使用 DataStore 存储所有配置 → Tavily Key 复用 DataStore，无需 EncryptedSharedPreferences
- 当前答题流程是 **3 层**级联（L1 Excel → L2 Wiki → L3 LLM 分支），不是设计文档说的 4 层
- ExtractedTextBus.AnswerSource 有 4 个枚举值，SidebarState.Done 有 3 个字段
- 设计文档中提到的 **3090 本地代理模式不存在**，不实现
- 当前 `SolvePipeline.kt:125` 有 `CancellationException` 被吞掉的 bug，顺便修复

### Metis Review
**Identified Gaps** (addressed with defaults):
- **终端 vs 上下文注入**: 默认上下文注入（搜索结果作为 LLM 上下文，类似 KB_INFER 模式）
- **API Key 存储**: 默认 DataStore（与现有 LLM API Key 一致）
- **引用展示**: 默认在 Done 状态下方显示可点击的 URL 列表
- **测试策略**: 默认 Agent QA only（无单元测试基础设施）
- **搜索状态**: 复用 Loading state，消息改为 "正在搜索相关参考资料…"
- **配额管理**: V1 跳过，不实现

---

## Work Objectives

### Core Objective
在现有答题流程的 L2（Wiki 知识库匹配）和 L3（LLM 答题）之间插入一个 Tavily 联网搜索层，当本地知识库匹配不到时，通过 Tavily API 搜索网络相关内容，将结果注入 LLM 生成带引用的答案。

### Concrete Deliverables
- `network/TavilyClient.kt` — Tavily API 客户端，含 `search()` 方法
- `pipeline/SearchManager.kt` — 搜索管理层，含关键词提取 + 结果归并
- `data/AppConfig.kt` — 新增 `tavilyApiKey` key、Flow、setter、getSnapshot 字段
- `util/ExtractedTextBus.kt` — 新增 `AnswerSource.SEARCH_MATCH`、扩展 `Done` 带 `references`
- `pipeline/SolvePipeline.kt` — 插入搜索层 + 修复 CancellationException
- `ui/screen/SettingsScreen.kt` — 新增 Tavily API Key 输入卡片
- `ui/sidebar/SidebarPanel.kt` — Done 状态增加引用链接展示

### Definition of Done
- [ ] 所有 7 个文件修改/新建完成
- [ ] 项目编译通过（`./gradlew assembleDebug`）
- [ ] 设置页可输入/保存/读取 Tavily API Key
- [ ] 搜索层在 Key 为空时静默跳过，Key 有效时正确执行
- [ ] 搜索成功时答案来源显示 "🔍 网络搜索" 并带引用链接
- [ ] 搜索失败时优雅降级到 LLM 裸答
- [ ] CancellationException 不再被吞掉

### Must Have
- [ ] Tavily API 客户端正确调用搜索端点和处理响应
- [ ] 搜索结果作为额外上下文注入 LLM（类似 KB_INFER 模式）
- [ ] 搜索层在所有错误场景下的优雅降级（401/429/超时/空结果/Key 为空）
- [ ] 设置页可配置 Tavily API Key（密码模式输入）
- [ ] Done 状态显示引用来源标签和可点击链接
- [ ] OkHttp 超时配置：connect=10s, read=15s

### Must NOT Have (Guardrails)
- ❌ 3090 本地代理模式（`Environment` 枚举、`detectEnvironment()`、代理路由）
- ❌ EncryptedSharedPreferences 加密存储（使用 DataStore 保持一致性）
- ❌ SharedPreferences 配额管理器（配额管理在 V1 跳过）
- ❌ Tavily `/extract` 端点（V1 仅 search）
- ❌ 搜索缓存机制（V1 不缓存）
- ❌ 并行搜索+LLM（V1 保持顺序级联）
- ❌ "测试连接"按钮（匹配现有模式：保存时不验证）
- ❌ 主动网络状态检测（通过 IOException 隐式降级）
- ❌ 新的 SidebarState 子类型（复用 Loading 状态）
- ❌ `search_depth`/`max_results` 用户可配置（V1 硬编码 basic/3）

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: NO (Android project without unit test infrastructure)
- **Automated tests**: None (no test framework configured)
- **Agent QA**: ALWAYS — each task includes concrete QA scenarios

### QA Policy
Every task MUST include agent-executed QA scenarios:
- **API/Backend (TavilyClient, SearchManager)**: Use Bash with curl to verify HTTP behavior, or run Android emulator commands
- **UI (SettingsScreen, SidebarPanel)**: Compose runs in Android — QA via code review of pattern matching with existing code
- **Pipeline (SolvePipeline)**: Logic verification by reading source and tracing through conditional branches
- Evidence saved to `.omo/evidence/task-{N}-{scenario-slug}.{ext}`

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation — 2 tasks, parallel):
├── Task 1: TavilyClient.kt (network layer)
└── Task 2: AppConfig.kt — Add tavilyApiKey

Wave 2 (Core logic — 4 tasks, after Wave 1):
├── Task 3: SearchManager.kt (search orchestration)
├── Task 4: ExtractedTextBus.kt — Add SEARCH_MATCH + references
├── Task 5: SolvePipeline.kt — Insert search tier + fix CancellationException
└── Task 6: SettingsScreen.kt — Add Tavily API Key card

Wave 3 (UI + Polish — 2 tasks, after Wave 2):
├── Task 7: SidebarPanel.kt — Render reference links in Done state
└── Task 8: SidebarPanel.kt — Update Loading state for "searching" message

Wave FINAL (After ALL — 4 parallel reviews):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
```

### Dependency Matrix
- **Task 1 (TavilyClient)**: - → Blocks 3, 5
- **Task 2 (AppConfig)**: - → Blocks 3, 5, 6
- **Task 3 (SearchManager)**: 1, 2 → Blocks 5
- **Task 4 (ExtractedTextBus)**: - → Blocks 5, 7
- **Task 5 (SolvePipeline)**: 1, 2, 3, 4 → Blocks nothing (tested via compile)
- **Task 6 (SettingsScreen)**: 2 → Blocks nothing (independent)
- **Task 7 (SidebarPanel Done)**: 4 → Blocks nothing
- **Task 8 (SidebarPanel Loading)**: - → Blocks nothing

### Agent Dispatch Summary
- **Wave 1**: 2 tasks — quick
- **Wave 2**: 4 tasks — deep (logic), unspecified-high (UI)
- **Wave 3**: 2 tasks — visual-engineering (UI compose)
- **FINAL**: 4 parallel reviews

---

## TODOs

- [x] 1. **创建 `TavilyClient.kt`**

  **What to do**:
  - 在 `network/` 包下创建 `TavilyClient.kt`
  - 实现 `suspend fun search(query, maxResults=3, includeAnswer=true): Result<SearchResult>` 方法
  - 使用 OkHttp 调用 `POST https://api.tavily.com/search`
  - 请求头: `Content-Type: application/json`, `Authorization: Bearer $apiKey`
  - 请求体: `{ query, max_results, include_answer, search_depth: "basic" }`
  - OkHttp 超时: connect=10s, read=15s (比 LLMClient 的 30/120 更激进)
  - 使用 `withContext(Dispatchers.IO)` 执行网络请求
  - 解析响应为 `TavilyResponse` (answer + results[].title/url/content/score)
  - 返回 `Result.success(SearchResult(answer, references, "tavily"))` 或 `Result.failure(exception)`
  - 异常处理: 所有网络/解析异常通过 `Result.failure` 返回，不抛异常
  - 数据类定义:
    - `data class SearchResult(val answer: String?, val references: List<Reference>, val source: String)`
    - `data class Reference(val title: String, val url: String, val snippet: String)`
    - `data class TavilyResponse(val answer: String?, val results: List<TavilyResult>?)`
    - `data class TavilyResult(val title: String, val url: String, val content: String, val score: Double)`

  **Must NOT do**:
  - ❌ 不实现 `/extract` 端点
  - ❌ 不实现缓存
  - ❌ 不使用 EncryptedSharedPreferences
  - ❌ 不添加新的 Gradle 依赖（OkHttp/Gson/Coroutines 已存在）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单一职责的网络客户端，模式与 LLMClient.kt 高度一致
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 2)
  - **Parallel Group**: Wave 1 (with Task 2)
  - **Blocks**: Task 3, Task 5
  - **Blocked By**: None

  **References**:
  - `LLMClient.kt:33-44` — OkHttpClient.Builder 配置模式（超时、拦截器）
  - `LLMClient.kt:97-143` — `chatSync()` 方法的 OkHttp 调用模式（await、Result 封装）
  - 设计文档 `TavilyClient.kt` 代码片段（第 99-188 行）

  **Acceptance Criteria**:
  - [ ] 文件创建在 `network/TavilyClient.kt` 正确位置
  - [ ] `search()` 方法使用 OkHttp 而不是其他 HTTP 库
  - [ ] OkHttp 超时配置: connect=10s, read=15s

  **QA Scenarios**:
  ```
  Scenario: TavilyClient 创建验证
    Tool: Bash (grep)
    Steps:
      1. grep for "class TavilyClient" in network/TavilyClient.kt
      2. grep for "api.tavily.com/search" in the file
      3. grep for "connectTimeout.*10" in the file
      4. grep for "readTimeout.*15" in the file
    Expected Result: 所有 4 个模式都找到了
    Evidence: .omo/evidence/task-1-class-exists.txt

  Scenario: 数据类定义验证
    Tool: Bash (grep)
    Steps:
      1. grep for "data class SearchResult" — must have answer, references, source
      2. grep for "data class Reference" — must have title, url, snippet
      3. grep for "data class TavilyResponse" — must have answer, results
    Expected Result: 所有 3 个数据类定义都存在
    Evidence: .omo/evidence/task-1-data-classes.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证文件存在和关键模式

  **Commit**: YES
  - Message: `feat(network): add TavilyClient for cloud search API`
  - Files: `app/src/main/java/com/examhelper/app/network/TavilyClient.kt`

- [x] 2. **扩展 `AppConfig.kt` — 新增 `tavilyApiKey` 配置**

  **What to do**:
  - 在 `AppConfig.kt` 的 companion object 中新增 `KEY_TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")`
  - 新增 `val tavilyApiKey: Flow<String>` 属性，默认返回 `""`
  - 新增 `suspend fun setTavilyApiKey(key: String)` 方法
  - 在 `ConfigSnapshot` 数据类中新增 `val tavilyApiKey: String = ""` 字段
  - 在 `getSnapshot()` 方法中读取 `prefs[KEY_TAVILY_API_KEY] ?: ""`
  - 保持与现有 LLM API Key 完全一致的模式（参考 `apiKey` 的实现）

  **Must NOT do**:
  - ❌ 不引入 EncryptedSharedPreferences
  - ❌ 不修改现有的 API Key 存储方式

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 纯机械扩展，完全遵循现有模式
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 1)
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: Task 3, Task 5, Task 6
  - **Blocked By**: None

  **References**:
  - `AppConfig.kt:22-23` — `KEY_API_KEY` 的定义模式
  - `AppConfig.kt:61-63` — `apiKey` Flow 属性的实现
  - `AppConfig.kt:96-99` — `setApiKey()` 方法的实现
  - `AppConfig.kt:129-140` — `getSnapshot()` 中读取 apiKey
  - `AppConfig.kt:143-151` — `ConfigSnapshot` 数据类定义

  **Acceptance Criteria**:
  - [ ] `KEY_TAVILY_API_KEY` 常量定义在 companion object 中
  - [ ] `tavilyApiKey` Flow 属性存在，默认返回 `""`
  - [ ] `setTavilyApiKey()` suspend 方法存在
  - [ ] `ConfigSnapshot` 包含 `tavilyApiKey: String = ""` 字段
  - [ ] `getSnapshot()` 读取 `KEY_TAVILY_API_KEY`

  **QA Scenarios**:
  ```
  Scenario: AppConfig 扩展验证
    Tool: Bash (grep)
    Steps:
      1. grep for "tavily_api_key" in AppConfig.kt — must be in KEY_TAVILY_API_KEY
      2. grep for "tavilyApiKey" in AppConfig.kt — must appear as Flow<String>, setter, and ConfigSnapshot field
    Expected Result: 所有模式都存在
    Evidence: .omo/evidence/task-2-appconfig.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证所有新增元素

  **Commit**: YES
  - Message: `feat(config): add tavilyApiKey to AppConfig and DataStore`
  - Files: `app/src/main/java/com/examhelper/app/data/AppConfig.kt`

- [x] 3. **创建 `SearchManager.kt`**

  **What to do**:
  - 在 `pipeline/` 包下创建 `SearchManager.kt`
  - `class SearchManager(private val tavilyClient: TavilyClient?)` — client 可为 null（跳过搜索）
  - `suspend fun searchQuestions(extractedText: String): SearchEnhancement` 入口方法
  - **流程**:
    1. 如果 `tavilyClient == null` → 返回 `SearchEnhancement(skipped = true)`
    2. 调用 `extractSearchQueries(text)` 提取搜索关键词
    3. 最多取前 3 个查询，逐个调用 `tavilyClient.search()`
    4. 只要有 2 个成功结果就提前停止
    5. 合并所有结果的 answer 和 references
    6. 使用 `distinctBy { it.url }` 去重引用
  - **关键词提取** `extractSearchQueries(text)`:
    1. 模式1：提取包含 `(` / `（` 的题干句子，去掉括号内容，取前 80 字符
    2. 模式2：前 3 个题干句子拼接，取前 120 字符（兜底）
    3. 过滤长度 ≤8 的查询
  - 数据类 `SearchEnhancement(skipped, failed, found, summary, references)`

  **Must NOT do**:
  - ❌ 不实现搜索缓存
  - ❌ 不对搜索结果做本地评分/排名

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 逻辑编排+模式匹配，需要理解现有 KB_INFER 上下文注入模式
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 4, Task 6)
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 5
  - **Blocked By**: Task 1, Task 2

  **References**:
  - `SolvePipeline.kt:62-81` — KB_INFER 模式的上下文构建方式（搜索结果注入 LLM 时参考此模式）
  - 设计文档 `SearchManager.kt` 代码片段（第 198-259 行）
  - `TavilyClient.kt`（Task 1）— 使用的客户端

  **Acceptance Criteria**:
  - [ ] `SearchManager` 类定义在 `pipeline/` 包下
  - [ ] `searchQuestions()` 方法遵循正确的降级逻辑
  - [ ] `extractSearchQueries()` 从文本中正确提取关键词
  - [ ] 结果合并使用 `distinctBy { it.url }` 去重

  **QA Scenarios**:
  ```
  Scenario: SearchManager 创建验证
    Tool: Bash (grep)
    Steps:
      1. grep for "class SearchManager" in pipeline/SearchManager.kt
      2. grep for "fun searchQuestions" in the file
      3. grep for "fun extractSearchQueries" in the file
      4. grep for "distinctBy" in the file
    Expected Result: 所有模式都存在
    Evidence: .omo/evidence/task-3-searchmanager.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证文件结构和关键方法

  **Commit**: YES
  - Message: `feat(pipeline): add SearchManager for query extraction and result merging`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SearchManager.kt`

- [x] 4. **扩展 `ExtractedTextBus.kt` — 新增 AnswerSource.SEARCH_MATCH + references**

  **What to do**:
  - 在 `AnswerSource` 枚举中新增: `SEARCH_MATCH("\uD83D\uDD0D 网络搜索")`
  - 在 `SidebarState.Done` 数据类中新增可选字段: `val references: List<Reference> = emptyList()`
  - 引用已有的 `Reference` 数据类（从 SearchManager/TavilyClient 导入，或定义为 top-level）
  - 确保 `Done` 的现有 3 个构造调用点不需要修改（因为有默认值）
  - `Done(text, answer, source, references = ...)` — 搜索成功时传入引用列表

  **Must NOT do**:
  - ❌ 不新增 `SidebarState` 子类型
  - ❌ 不修改现有的 `AnswerSource` 枚举值

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 纯机械扩展，遵循现有模式
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 3, Task 6)
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 5, Task 7
  - **Blocked By**: None

  **References**:
  - `ExtractedTextBus.kt:32-37` — `AnswerSource` 枚举定义
  - `ExtractedTextBus.kt:28` — `SidebarState.Done` 数据类定义
  - `SolvePipeline.kt:32,50,123-124` — Done 的 3 个现有调用点（验证默认值兼容）

  **Acceptance Criteria**:
  - [ ] `AnswerSource.SEARCH_MATCH("\uD83D\uDD0D 网络搜索")` 存在
  - [ ] `SidebarState.Done` 有 `references` 字段且有默认值 `emptyList()`
  - [ ] 现有的 3 个 `Done()` 调用点不需要修改（编译通过）

  **QA Scenarios**:
  ```
  Scenario: ExtractedTextBus 扩展验证
    Tool: Bash (grep)
    Steps:
      1. grep for "SEARCH_MATCH" in ExtractedTextBus.kt
      2. grep for "references" in ExtractedTextBus.kt — must be in Done data class with default
      3. grep for "Done(" in SolvePipeline.kt — 3 call sites should still compile
    Expected Result: 所有模式都存在
    Evidence: .omo/evidence/task-4-bus.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证所有新增元素

  **Commit**: YES
  - Message: `feat(bus): add AnswerSource.SEARCH_MATCH and references to Done state`
  - Files: `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt`

- [x] 5. **修改 `SolvePipeline.kt` — 插入 Tavily 搜索层 + 修复 CancellationException**

  **What to do**:
  **Part A — 插入搜索层**（在 L2 和 LLM 之间）:
  - 在 L2 的 early-return（当前第 53 行 `return`）之后、LLM 分支（当前第 55 行 `var llmSource`）之前插入：
  ```kotlin
  // L3: Tavily 联网搜索
  val config = ExamApplication.instance.appConfig.getSnapshot()
  if (config.tavilyApiKey.isNotBlank()) {
      ExtractedTextBus.updateSidebarState(
          SidebarState.Loading("正在搜索相关参考资料...", requestStartMs, maxTokens)
      )
      val tavilyClient = TavilyClient(config.tavilyApiKey)
      val searchManager = SearchManager(tavilyClient)
      val enhancement = searchManager.searchQuestions(text)
      
      if (enhancement.found) {
          // 搜索结果注入 LLM（类似 KB_INFER 模式）
          val searchContext = buildSearchContext(enhancement)
          effectiveMessage = if (effectiveMessage != userMessage) {
              "$searchContext\n\n$effectiveMessage"
          } else {
              "$searchContext\n\n$userMessage"
          }
          // 标记来源，但后续仍然走 LLM（上下文注入模式）
          // 最终 Done 状态会显示 SEARCH_MATCH
      }
      // enhancement.failed 或 enhancement.skipped -> 静默降级，继续 LLM
  }
  ```
  - 新增 `private fun buildSearchContext(enhancement: SearchEnhancement): String` 方法
  - 在 LLM 完成后的 `SidebarState.Done` 中：
    - 如果 `llmSource` 被标记为搜索增强，使用 `AnswerSource.SEARCH_MATCH`
    - 传入搜索结果的 `references` 列表
  - 添加 `import` for TavilyClient, SearchManager, SearchEnhancement

  **Part B — 修复 CancellationException 吞掉 bug**:
  - 在当前的 catch 块（第 125 行 `catch (e: Exception)`）中：
  - 在 catch 块第一行添加：
  ```kotlin
  if (e is kotlinx.coroutines.CancellationException) throw e
  ```
  - 这确保协程取消不会显示为"请求异常"错误

  **Must NOT do**:
  - ❌ 不修改 L1/L2 的 existing logic
  - ❌ 不在搜索层使用 `AnswerSource.SEARCH_MATCH` 做 early-return（保持上下文注入）
  - ❌ 不添加 3090 代理模式

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 核心逻辑修改，需要精确理解现有级联流程
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 6)
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Task 1, Task 2, Task 3, Task 4

  **References**:
  - `SolvePipeline.kt:24-53` — L1/L2 现有逻辑（插入点之前的代码）
  - `SolvePipeline.kt:55-81` — KB_INFER 上下文构建模式（参考 buildSearchContext）
  - `SolvePipeline.kt:88-130` — LLM 调用和 Done 发射逻辑（需修改 Done 调用）
  - `SolvePipeline.kt:125` — catch 块（CancellationException 修复点）
  - `SearchManager.kt`（Task 3）— 调用的搜索管理层
  - KBEngine.kt 的 `getAnswerFromKB()` — 参考现有上下文注入的 prompt 格式

  **Acceptance Criteria**:
  - [ ] 搜索层插入在 L2 `return` 之后、LLM 分支之前
  - [ ] Tavily API Key 为空时跳过搜索层
  - [ ] 搜索成功时注入上下文到 effectiveMessage
  - [ ] 搜索失败时静默降级（不中断流程）
  - [ ] `CancellationException` 在 catch 块中被 rethrow
  - [ ] `buildSearchContext()` 方法存在

  **QA Scenarios**:
  ```
  Scenario: Pipeline 修改验证
    Tool: Bash (grep)
    Steps:
      1. grep for "TavilyClient\|SearchManager" in SolvePipeline.kt — 新 import
      2. grep for "正在搜索相关参考资料" in SolvePipeline.kt — 搜索状态消息
      3. grep for "CancellationException" in SolvePipeline.kt — bug 修复
      4. grep for "fun buildSearchContext" in SolvePipeline.kt — 新方法
      5. grep for "tavilyApiKey.isNotBlank" in SolvePipeline.kt — Key 检查
    Expected Result: 所有 5 个模式都存在
    Evidence: .omo/evidence/task-5-pipeline.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证所有新增代码结构

  **Commit**: YES
  - Message: `feat(pipeline): insert Tavily search tier in SolvePipeline + fix CancellationException`
  - Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`

- [x] 6. **修改 `SettingsScreen.kt` — 新增 Tavily API Key 设置卡片**

  **What to do**:
  - 在现有 API Key 设置卡片之后、模型名称之前，插入 Tavily API Key 卡片
  - 使用现有 `SettingsCard` 组件模式：
  - 图标: `Icons.Filled.Search` 或类似
  - 标题: `"Tavily API Key（联网搜索）"`
  - placeholder: `"tvly-...（可选，免费 1000 次/月）"`
  - `isPassword = true`（密码模式）
  - 添加状态变量 `var tavilyApiKey by remember { mutableStateOf("") }`
  - 在 `LaunchedEffect` 中从 `snapshot.tavilyApiKey` 加载
  - 在保存按钮中调用 `appConfig.setTavilyApiKey(tavilyApiKey)`
  - 在 `saveMessage` 提示中增加 Tavily Key 已保存的确认（可选）

  **Must NOT do**:
  - ❌ 不加"测试连接"按钮
  - ❌ 不加 API Key 格式验证
  - ❌ 不加配额使用量显示

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose UI 修改，需要匹配现有 Material3 主题和卡片样式
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 3, Task 4, Task 5)
  - **Parallel Group**: Wave 2
  - **Blocks**: None
  - **Blocked By**: Task 2

  **References**:
  - `SettingsScreen.kt:136-143` — 现有 API Key 卡片的完整模式
  - `SettingsScreen.kt:136` — `Icons.Filled.Key` 图标使用
  - `SettingsScreen.kt:87-94` — `LaunchedEffect` 加载配置
  - `SettingsScreen.kt:241-256` — 保存按钮逻辑
  - `AppConfig.kt`（Task 2）— 新增的 tavilyApiKey setter/getter

  **Acceptance Criteria**:
  - [ ] Tavily API Key 输入卡片出现在设置页
  - [ ] 使用 `isPassword = true`（密码遮罩）
  - [ ] 值从 `ConfigSnapshot.tavilyApiKey` 加载
  - [ ] 保存在 `appConfig.setTavilyApiKey()` 中

  **QA Scenarios**:
  ```
  Scenario: 设置页扩展验证
    Tool: Bash (grep)
    Steps:
      1. grep for "tavilyApiKey" in SettingsScreen.kt — 状态变量
      2. grep for "Tavily" in SettingsScreen.kt — 标题文本
      3. grep for "setTavilyApiKey" in SettingsScreen.kt — 保存调用
      4. grep for "tvly-" in SettingsScreen.kt — placeholder
    Expected Result: 所有 4 个模式都存在
    Evidence: .omo/evidence/task-6-settings.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证所有新增 UI 元素

  **Commit**: YES
  - Message: `feat(settings): add Tavily API Key configuration card`
  - Files: `app/src/main/java/com/examhelper/app/ui/screen/SettingsScreen.kt`

- [x] 7. **修改 `SidebarPanel.kt` — Done 状态展示引用链接**

  **What to do**:
  - 在 `SidebarState.Done` 的渲染区域中（当前第 250-345 行），在来源标签之后、答案文本之前增加引用链接展示：
  ```kotlin
  // 引用链接展示（如果有）
  if (s.references.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      SectionHeader("参考资料")
      Column {
          s.references.take(5).forEachIndexed { index, ref ->
              Row(modifier = Modifier.padding(vertical = 2.dp)) {
                  Text(
                      text = "[${index + 1}] ",
                      color = MaterialTheme.colorScheme.primary,
                      fontSize = 12.sp
                  )
                  Text(
                      text = ref.title,
                      color = Color.White.copy(alpha = 0.8f),
                      fontSize = 12.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f)
                  )
              }
              Text(
                  text = ref.url,
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                  fontSize = 10.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
              )
          }
      }
  }
  ```
  - 引用 `Reference` 数据类（从 SearchManager 导入路径）
  - 来源标签文字改为动态：如果 `s.source == AnswerSource.SEARCH_MATCH`，显示 "🔍 网络搜索"
  - 最大显示 5 条引用

  **Must NOT do**:
  - ❌ 不实现折叠/展开动画（V1 直接展开）
  - ❌ 不实现点击打开浏览器（Compose 中需要 Intent，V1 只显示 URL）
  - ❌ 不新增 SidebarState 子类型

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose UI 修改，需要匹配现有暗色主题和排版风格
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 8)
  - **Parallel Group**: Wave 3
  - **Blocks**: None
  - **Blocked By**: Task 4

  **References**:
  - `SidebarPanel.kt:250-273` — Done 状态的现有渲染逻辑
  - `SidebarPanel.kt:253-260` — 来源标签渲染（`s.source.label`）
  - `SidebarPanel.kt:398-408` — `SectionHeader` 组件复用
  - `ExtractedTextBus.kt` — `AnswerSource.SEARCH_MATCH` 和 `Done.references`

  **Acceptance Criteria**:
  - [ ] 引用链接区域在 `s.references` 非空时显示
  - [ ] 最多显示 5 条引用
  - [ ] 每条引用显示序号、标题、URL
  - [ ] 来源标签正确显示 "🔍 网络搜索"

  **QA Scenarios**:
  ```
  Scenario: 引用链接展示验证
    Tool: Bash (grep)
    Steps:
      1. grep for "references" in SidebarPanel.kt — 引用渲染块
      2. grep for "参考资料" in SidebarPanel.kt — 引用区域标题
      3. grep for "SEARCH_MATCH" in SidebarPanel.kt — 来源标签判断
      4. grep for "take(5)" in SidebarPanel.kt — 5 条限制
    Expected Result: 所有 4 个模式都存在
    Evidence: .omo/evidence/task-7-references.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证所有新增 UI 元素

  **Commit**: YES
  - Message: `feat(sidebar): show reference links in Done state`
  - Files: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt`

- [x] 8. **修改 `SidebarPanel.kt` — Loading 状态适配搜索消息**

  **What to do**:
  - 不需要新的 SidebarState 子类型，复用现有的 `SidebarState.Loading` 机制
  - 搜索层会设置 `SidebarState.Loading("正在搜索相关参考资料...", requestStartMs, maxTokens)`
  - 现有的 Loading 渲染（第 186-211 行）已经能正确显示任意 message 字符串
  - **确认** Loading 状态的 message 显示逻辑可以正常显示 "正在搜索相关参考资料..."
  - 如果底部的状态栏文字（第 381-392 行）有特定字符串匹配，需要适配

  **Must NOT do**:
  - ❌ 不新增 SidebarState 子类型
  - ❌ 不修改 Loading 数据类

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 简单确认，不需修改或改动极小
  - **Skills**: None needed

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 7)
  - **Parallel Group**: Wave 3
  - **Blocks**: None
  - **Blocked By**: None

  **References**:
  - `SidebarPanel.kt:186-211` — Loading 状态渲染（动态 message）
  - `SidebarPanel.kt:381-392` — 底部状态栏文字

  **Acceptance Criteria**:
  - [ ] Loading 状态能正确显示包含"正在搜索相关参考资料"的自定义消息
  - [ ] 底部状态栏在搜索时显示相应状态文字

  **QA Scenarios**:
  ```
  Scenario: 搜索状态验证
    Tool: Bash (grep)
    Steps:
      1. grep for "正在搜索相关参考资料" in SidebarPanel.kt — 虽然消息在 SolvePipeline 设置，但确认渲染不限制消息内容
      2. grep for "Loading" in SidebarPanel.kt — 确认 Loading 渲染使用动态 message
    Expected Result: Loading 渲染使用 message 参数（动态），不需要硬编码
    Evidence: .omo/evidence/task-8-loading.txt
  ```

  **Evidence to Capture:**
  - [ ] grep 输出验证

  **Commit**: YES (with Task 7)
  - Message: `feat(sidebar): show reference links and update loading state for search`
  - Files: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt`

---

## Final Verification Wave

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, check logic). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .omo/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew assembleDebug` (or verify it would pass). Review all changed files for: type suppression, empty catches, debug logging in prod, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names (data/result/item/temp).
  Output: `Build [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`
  Start from clean state. Verify compile succeeds. Review each task's acceptance criteria and confirm each is met by reading the implementation. Check cross-task integration (features working together, not isolation). Check edge cases: empty API key, invalid key, network error.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination: Task N touching Task M's files.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Task 1**: `feat(network): add TavilyClient for cloud search API`
  Files: `app/src/main/java/com/examhelper/app/network/TavilyClient.kt`
- **Task 2**: `feat(config): add tavilyApiKey to AppConfig and DataStore`
  Files: `app/src/main/java/com/examhelper/app/data/AppConfig.kt`
- **Task 3**: `feat(pipeline): add SearchManager for query extraction and result merging`
  Files: `app/src/main/java/com/examhelper/app/pipeline/SearchManager.kt`
- **Task 4**: `feat(bus): add AnswerSource.SEARCH_MATCH and references to Done state`
  Files: `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt`
- **Task 5**: `feat(pipeline): insert Tavily search tier in SolvePipeline + fix CancellationException bug`
  Files: `app/src/main/java/com/examhelper/app/pipeline/SolvePipeline.kt`
- **Task 6**: `feat(settings): add Tavily API Key configuration card`
  Files: `app/src/main/java/com/examhelper/app/ui/screen/SettingsScreen.kt`
- **Task 7-8**: `feat(sidebar): show reference links and update loading state for search`
  Files: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt`

---

## Success Criteria

### Verification Commands
```bash
cd C:\Users\lasal\i_exam
./gradlew assembleDebug  # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent — no 3090 proxy, no EncryptedSharedPreferences, no new SidebarState subtype
- [ ] Project compiles successfully
