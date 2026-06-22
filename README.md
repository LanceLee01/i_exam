# 考试助手 (ExamHelper) v3.1.0

> **i国网** 自动答题工具 — AccessibilityService 读屏 + 4 级答题管道 + 悬浮窗侧边栏 + 自动点击

## 功能概览

| 功能 | 说明 |
|:---|:---|
| **屏幕读取** | AccessibilityService 遍历 i国网 页面节点树，滚动采集全部题目 |
| **水印过滤** | 自动过滤"非涉密平台""严禁处理"等干扰水印 |
| **4 级答题管道** | L1 题库匹配 → L2 Wiki 检索 → L3 联网搜索 → L4 LLM 流式答题，逐级提前返回 |
| **SSE 流式解答** | OkHttp SSE 接收 LLM 回复，实时推送到悬浮侧边栏 |
| **自动点击** | 解析 `[题号] 选项` 格式答案，通过 Accessibility 手势自动点击 |
| **双层知识库** | Excel 题库（JSON 持久化）+ Wiki 知识库（Room/FTS4 + Embedding 语义检索） |
| **增量去重** | SHA-256 哈希缓存，避免重复导入相同文件 |
| **多 KB 管理** | 支持创建多个知识库，可随时切换 |

## 系统要求

| 项目 | 要求 |
|:---|:---|
| **操作系统** | Android 8.0+ (API 26) |
| **LLM 服务** | OpenAI 协议兼容的 API（任何 `/v1/chat/completions` 端点） |
| **权限** | 悬浮窗权限 + 无障碍服务 + 通知权限（Android 13+ `POST_NOTIFICATIONS`） |
| **开发环境** | JDK 17+、Android SDK 36、Gradle 9.5.1 |

## 快速开始

### 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 首次启动引导

打开 App → 4 步引导页：

1. **了解功能** — 确认应用用途
2. **开启悬浮窗** — 系统设置 → 在其他应用上层显示
3. **开启无障碍服务** — 系统设置 → 已安装的服务 → 「考试助手」→ 开启
4. **配置 API** — 填入 LLM 端点地址和 API Key

### 考试流程

1. 打开 i国网 App，进入考试页面
2. 从屏幕右边缘左滑展开悬浮侧边栏（或点击边缘指示条）
3. 点击 **「读取屏幕」** → 无障碍服务自动滚动提取全部题目
4. 确认预览内容无误，点击 **「解答」**
5. 等待答案流式输出（L1 命中可能瞬间返回）
6. 点击 **「自动填入」** → 逐题自动点击选项

---

## 技术架构

```
┌──────────────────────────────────────────────────────────┐
│                     MainActivity                          │
│  !setupComplete → WelcomeScreen                          │
│  setupComplete  → MainScreen (4-Tab Bottom Nav)          │
│       ├── HomeTab          (首页)                        │
│       ├── QuestionBankTab  (题库管理)                     │
│       ├── KnowledgeBaseTab (知识库管理)                   │
│       └── SettingsTab      (API 配置)                    │
├──────────────────────────────────────────────────────────┤
│  SidebarService (前台 Service)                            │
│  └── SidebarPanel (Compose 悬浮窗, 65%×80% 屏幕)          │
│       └── SolvePipeline (答题引擎)                        │
├──────────────────────────────────────────────────────────┤
│  ExamAccessibilityService (无障碍 Service)                 │
│       ├── extractAndSendText()   (读屏)                  │
│       └── performAutoClick()     (自动点击)              │
├──────────────────────────────────────────────────────────┤
│  通信总线: ExtractedTextBus (StateFlow + SharedFlow)       │
├──────────────────────────────────────────────────────────┤
│  知识库层                                                │
│  ├── KnowledgeBaseManager (Excel KB, JSON 持久化)         │
│  └── KBEngine (Wiki KB, Room/FTS4/Embedding)             │
├──────────────────────────────────────────────────────────┤
│  网络层                                                  │
│  ├── LLMClient       (OkHttp SSE 流式)                   │
│  ├── TavilyClient     (联网搜索)                          │
│  ├── EmbeddingClient  (文本向量化)                        │
│  └── RemoteTikuClient (远程题库下载)                      │
└──────────────────────────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术 | 版本 |
|:---|:---|:---|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.09 |
| 构建 | Gradle KTS + KSP | AGP 8.7.3 / KSP 1.0.25 |
| 数据库 | Room + SQLite FTS4 | 2.6.1 |
| 配置 | DataStore Preferences | 1.1.1 |
| 网络 | OkHttp + SSE | 4.12.0 |
| JSON | Gson | 2.11.0 |
| 文档解析 | Apache POI | 5.2.5 |
| PDF | PDFBox Android | 2.0.27.0 |
| 测试 | JUnit 5 + MockK + Turbine | 5.11 / 1.13.12 / 1.1.0 |

**架构特点：**
- **无 DI 框架** — 依赖通过 `ExamApplication.instance` 静态单例和直接构造函数注入
- **无 Jetpack Navigation** — 使用状态机 `when` 切换 Composable
- **无版本目录** — 依赖版本全部硬编码在 `build.gradle.kts`
- **无 instrumentation test** — 仅有 JUnit 5 本地单测
- **自定义 Compose 生命周期** — `WindowLifecycleOwner` 管理悬浮窗的 ViewTree 生命周期

---

## 4 级答题管道

`solve(text)` 从 L1 到 L4 逐级执行，任一级命中即提前返回：

```
L1 (Excel KB)      ~1-3ms     ≥0.50 → 直接返回 ✅      命中率 ~25%
  ↓ 未命中
L2 (Wiki KB)       ~10-25ms   ≥0.50 → 直接返回 ✅      命中率 ~10%
  ↓ 未命中         (当前未接入管道)
L3 (Tavily 搜索)   ~1-7s      找到 → 拼接参考资料         进入率 ~65%
  ↓
L4 (LLM SSE)       ~5-30s     兜底答题                   进入率 ~65%
```

**L1 优先覆盖 L4** — 同一题号在 L1 和 L4 都给出答案时，L1 覆盖 L4。

### L1 — Excel 题库匹配

**核心算法** (`KnowledgeBase.search()`)：两阶段混合评分。

1. **精确子串匹配**（快速路径） — query 包含 KB 题目 → score=1.0
2. **字符集预过滤** — CharSet 重叠 < 10% 直接跳过
3. **Trigram 快速筛选** — Jaccard < 0.12 直接跳过
4. **混合评分** — `Trigram×50% + Bigram×30% + Token×20%`
5. **选项文本加权** — `score×0.6 + optJaccard×0.4`（有选项数据时）
6. **LCS 救援** — 边界匹配 (0.15~0.50) 时用最长公共子序列提分
7. **长度偏置排序** — `score + min(len/500, 0.05)` 防止短题抢匹配

**题号定位** (`findQuestionNumber()`)：先精确子串匹配 → 再模糊混合匹配 → 题目块验证（至少 2 个选项字母或 20+ 字符）。

**冲突消解**：同一题号多答案时，三优先级消解：
1. 选项文本匹配（trigram Jaccard ≥ 0.50 且答案一致）
2. 题型过滤 + 分数排序（`score×1000 + question.length`）
3. 未消解 → 下放至 LLM

**空题干保护**：有效汉字 < 3 且总字符 < 10 → 标记"不确定"、跳过 LLM。
**空题干选项救援**：题干空但选项全时，先用选项文本 trigram 匹配题库（判断题跳过），fallback 到 search。

### L2 — Wiki 知识库检索

`KBEngine.searchByQuestion()` 优先 Embedding 语义检索，fallback SQL LIKE。

- **Embedding 搜索**（优先）：调用 `BAAI/bge-large-zh-v1.5` 模型生成 query vector，与本地存储的 page embedding 做 cosine similarity → topK(10)
- **文本搜索**（回退）：`searchByTitleLike()` 做 SQL LIKE

*注：L2 的 `tryWikiMatchAll()` 已实现，但当前 `solve()` 从 L1 直接跳到 L3/L4，L2 未接入管道。*

### L3 — 联网搜索

- `SearchManager.extractSearchQueries()` 提取 1-3 个搜索查询
- `TavilyClient.search()` 串行调用最多 2 次（成功即 break）
- 结果拼接为 LLM prompt 的参考资料部分

### L4 — LLM 流式答题

- `LLMClient.chatStream()` OkHttp SSE 流式接收
- 支持 `reasoning_content`（DeepSeek/通义千问 think 模式），有 content 时优先显示 content
- ETA 估算：首次请求按 prompt 长度除以 1.5 估算 token 数，后续用实测速度
- 答案解析：`parseL4Answer()` 按 `[题号] 答案` 格式解析，补全 LLM 漏答的题目
- `formatCombinedAnswer()` 合并 L1 与 L4 结果（L1 覆盖 L4 同题号冲突）

---

## 通信总线

`ExtractedTextBus`（Kotlin `object` 单例）是核心通信枢纽：

```
SidebarPanel (UI)          ExamAccessibilityService (Service)      SolvePipeline (Engine)
      │                              │                                    │
      ├─ collect(sidebarState)       ├─ collect(events)                   ├─ updateSidebarState()
      ├─ sendEvent(ClickAnswer)      ├─ sendEvent(TextExtracted)          ├─ updateSidebarState()
      ├─ sendEvent(RequestExtract)   └─ 执行读屏/点击动作                   └─ updateSidebarState()
      └─ updateSidebarState()
```

**StateFlow：**
- `sidebarState: StateFlow<SidebarState>` — 驱动 Compose UI
- `accessibilityConnected: StateFlow<Boolean>` — 无障碍连接状态

**SharedFlow：**
- `events: SharedFlow<Event>` — `RequestExtract`、`ClickAnswer`、`TextExtracted`、`AccessibilityConnected/Disconnected`

**SidebarState 状态机：**

```
Idle → Loading → Preview → Streaming → Done → (返回 Preview → ...)
                → Answering → Done
                → Error
```

**@Volatile ETA 指标：** `lastTokensPerSec`、`lastPromptTokens`、`lastTtftMs`

---

## 双层知识库

### Excel KB (`KnowledgeBaseManager`)

| 项目 | 说明 |
|:---|:---|
| **存储** | `kb_data.json`（filesDir），Gson 全量序列化 |
| **数据结构** | `KnowledgeBase` { `id`, `name`, `entries: List<KBEntry>`, `importedHashes`, `importRecords` } |
| **KBEntry** | `question`, `answer`, `source`, `options`, `questionType` |
| **搜索** | 混合评分（见 L1 算法详解），含特征缓存加速 |
| **去重** | 文件 SHA-256 哈希 |

**Excel 导入格式：**

| A | B | C | D | E |
|:---|:---|:---|:---|:---|
| 题目 | 答案 | 来源（可选） | 选项文本（可选） | 题型（可选） |
| 电气设备发生火灾时，应首先？ | D | 2024 安全题库 | A.用水 B.干粉 C.二氧化碳 D.切断电源 | 单选题 |

**列自动检测**：`ColumnDetector` 根据表头关键字自动识别题目列和答案列。检测失败时可通过 API Key 判断是否需要 LLM 辅助检测。

### Wiki KB (`KBEngine` + Room DB)

| 表 | 说明 |
|:---|:---|
| `wiki_pages` | WikiPage 主表（title, content, summary, pageType, tags） |
| `wiki_pages_fts` | FTS4 全文索引表 |
| `wiki_pages_fts_content` | FTS4 内容影子表 |
| `wiki_page_embeddings` | Embedding 向量（BLOB），用于语义搜索 |
| `source_files` | 源文件记录（路径、SHA-256、导入时间） |
| `wikilinks` | 页面交叉引用（`[[ 标题 ]]` 解析） |

**导入流程：**

```
选择文件 (.txt/.md/.pdf/.pptx/.ppt/.xlsx)
  → readFileContent() 提取原文 (PDF/PPT/Excel 用 POI/PDFBox)
  → LLM buildWikiPrompt() 提示词 → LLM chatSync()
  → parseWikiPages() 解析 YAML 元数据 + Markdown 正文
  → 写入 Room DB (wiki_pages + wiki_pages_fts + source_files)
  → 解析 [[wikilinks]] 写入 wikilinks 表
  → 生成 embedding 向量写入 wiki_page_embeddings
```

- 文档截断上限：8000 字符（`MAX_DOC_CHARS`）
- 单页上限：10000 字符（`MAX_CONTENT_LENGTH`）
- 去重：基于内容 SHA-256

---

## 屏幕读取与自动点击

### 读屏流程

```
用户点击「读取屏幕」
  → ExtractedTextBus.sendEvent(RequestExtract)
  → ExamAccessibilityService.extractAndSendText()
     ├── 5 次 backward scroll → 回到页面顶部
     ├── 5 次 forward scroll → 逐段采集
     │   └── traverseNode() 递归遍历节点树
     │       ├── WatermarkFilter.shouldSkipNode() 过滤水印
     │       ├── 收集 node.text / contentDescription
     │       └── child.recycle() 释放节点  ✅
     ├── LinkedHashSet 去重
     └── cleanAndFormat() → 发送 TextExtracted 事件
```

### 自动点击流程

```
用户点击「自动填入」
  → ExtractedTextBus.sendEvent(ClickAnswer)
  → ExamAccessibilityService.performAutoClick()
     ├── findAllClickable() 收集全部可点击节点  ⚠️ 缺少 recycle
     ├── 按 Y 坐标排序 → 构建选项节点队列
     ├── parseAnswerPairs() 解析 [题号] 选项
     ├── extractQuestionTypes() 提取题型
     ├── 逐题处理：
     │   ├── 判断题 → clickToggleOption(正确/错误)
     │   ├── 选择题 → matchesSelection() 匹配 → ACTION_CLICK
     │   ├── 多选题 → 依次点击选项 → findConfirmButton() → 点击确认
     │   ├── "不确定" → 默认点 A + 找确认按钮
     │   └── 1.5s 间隔 + 随机延迟 80-300ms
     └── Toast 提示不确定的题目
```

### 无障碍服务配置

```xml
accessibilityEventTypes: typeAllMask
accessibilityFlags: flagDefault | flagIncludeNotImportantViews | flagRetrieveInteractiveWindows
canRetrieveWindowContent: true
canPerformGestures: true
notificationTimeout: 300ms
```

### 悬浮窗实现

- `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCH_MODAL`
- 边缘指示条：24dp 宽（4dp 可见 + 20dp 触摸区），右边缘置顶
- 面板：65% 屏宽 × 80% 屏高，右对齐
- 右滑 25% 宽度收起面板

---

## 项目结构

```
app/src/main/java/com/examhelper/app/
├── MainActivity.kt                # 单 Activity 入口（状态机导航）
├── ExamApplication.kt             # Application 单例（DB + Config + KB 初始化）
├── data/
│   └── AppConfig.kt              # DataStore 配置（含 ConfigSnapshot）
├── filter/
│   └── WatermarkFilter.kt        # 水印过滤（关键字匹配）
├── knowledge/
│   ├── KnowledgeBaseManager.kt   # Excel KB 管理器（JSON + 混合搜索 + 导入去重）
│   ├── KBEngine.kt              # Wiki KB 引擎（Room + LLM 解析 + Embedding）
│   ├── ColumnDetector.kt        # Excel 列自动检测
│   ├── ColumnMapping.kt         # Excel 列映射
│   ├── ZipImportHelper.kt       # ZIP 批量导入
│   └── db/
│       ├── AppDatabase.kt       # Room 数据库（v2，含 Embedding 迁移）
│       ├── WikiPage.kt          # Wiki 页面 + FTS4 索引
│       ├── WikiPageDao.kt       # Wiki DAO（CRUD + FTS 搜索）
│       ├── WikipageEmbedding.kt # Embedding 向量
│       ├── WikiPageEmbeddingDao.kt
│       ├── Wikilink.kt          # [[wikilink]] 交叉引用
│       ├── WikilinkDao.kt
│       ├── SourceFile.kt        # 源文件记录
│       └── SourceFileDao.kt
├── network/
│   ├── LLMClient.kt             # OpenAI 协议客户端（SSE 流式 + 同步）
│   ├── TavilyClient.kt          # Tavily 联网搜索
│   ├── EmbeddingClient.kt       # Embedding API（BAAI/bge-large-zh-v1.5）
│   └── RemoteTikuClient.kt      # 远程题库下载
├── pipeline/
│   ├── SolvePipeline.kt         # 4 级答题管道（L1-L4）
│   └── SearchManager.kt         # Tavily 搜索管理
├── service/
│   ├── SidebarService.kt        # 悬浮窗前台 Service
│   ├── ExamAccessibilityService.kt  # 无障碍 Service（读屏 + 自动点击）
│   └── WindowLifecycleOwner.kt  # 悬浮窗 Compose 生命周期
├── ui/
│   ├── sidebar/
│   │   ├── EdgeHandle.kt        # 右边缘指示条
│   │   ├── SidebarPanel.kt      # 悬浮面板主入口
│   │   ├── SidebarStateRenderer.kt  # 状态驱动渲染
│   │   ├── SidebarComponents.kt # 共享组件
│   │   └── SidebarActions.kt    # 操作按钮
│   ├── screen/
│   │   ├── MainScreen.kt        # 4 Tab 主界面 + BottomNav
│   │   ├── WelcomeScreen.kt     # 4 步引导页
│   │   ├── HomeTab.kt           # 首页 Tab
│   │   ├── QuestionBankTab.kt   # 题库管理 Tab
│   │   ├── KnowledgeBaseTab.kt  # 知识库管理 Tab
│   │   ├── SettingsTab.kt       # 设置 Tab
│   │   ├── WikiPageScreen.kt    # Wiki 页面详情
│   │   └── KbDetailScreen.kt    # 知识库详情
│   ├── components/
│   │   └── SharedComponents.kt
│   └── theme/
│       ├── Theme.kt             # Material 3 主题
│       ├── Type.kt              # 字体排印
│       ├── BottomNavBar.kt      # 玻璃态底部导航栏
│       └── Logo.kt              # Logo 组件
└── util/
    ├── ExtractedTextBus.kt      # 事件总线 + SidebarState 定义
    ├── AccessibilityParseUtils.kt   # 答案解析 + 题型提取
    ├── ExamConstants.kt         # 常量 + 动态正则
    ├── OptionTextUtils.kt       # 选项文本处理
    └── ReferenceFormatter.kt    # 参考资料格式化
```

---

## 开发指南

### 编译与测试

```bash
export JAVA_HOME="/path/to/jdk17"

# 构建
./gradlew :app:assembleDebug      # 调试版
./gradlew :app:assembleRelease    # 发布版

# 测试
./gradlew :app:test               # 全部单测
./gradlew :app:test --tests "com.examhelper.app.pipeline.SolvePipelineTest"
./gradlew :app:test --tests "com.examhelper.app.pipeline.SolvePipelineTest.methodName"

# 工具
./gradlew clean                   # 清理
./gradlew :app:lint               # Lint 检查
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 关键配置

| 配置 | 文件 | 默认值 |
|:---|:---|:---|
| compileSdk / targetSdk | build.gradle.kts | 36 |
| minSdk | build.gradle.kts | 26 |
| LLM 端点 | AppConfig / 设置页 | `https://opencode.ai/zen/go/v1` |
| 模型 | AppConfig / 设置页 | `deepseek-v4-flash` |
| Max Tokens | AppConfig / 设置页 | 10240 |
| Temperature | AppConfig / 设置页 | 0.3 |
| L1 匹配阈值 | SolvePipeline | 0.50 |
| Wiki 内容截断 | KBEngine | 8000 字符 / 10000 字每页 |
| 侧边栏尺寸 | SidebarService | 65% × 80% |
| 边栏宽度 | SidebarService | 24dp |

### 答案格式

```
[题号] 答案选项

[1] A
[2] B C          # 多选，空格分隔
[38] 正确         # 判断题
[15] 不确定       # 无法确定时
```

### LLM 端点配置

支持任何 OpenAI 协议兼容服务：
- 本地 llama.cpp：`http://<ip>:8082`
- DeepSeek：`https://api.deepseek.com`
- 其他兼容 API

`LLMClient.buildUrl()` 自动补全 `/v1/chat/completions` 后缀。

### System Prompt（默认）

```
你是考试答题助手。请认真阅读以下从考试界面提取的文字，其中可能包含水印等无关信息，请忽略它们。
直接给出答案，不要思考过程。识别出所有题目后，只输出题号和答案选项，不要解释理由。每行一题，格式严格如下：

[题号] 答案选项

重要规则：
- 单选题：输出单个字母，如 [1] A
- 判断题：输出 正确 或 错误，如 [38] 正确
- 多选题（最重要）：必须输出全部正确选项！如果正确答案是B和C，必须输出 [44] B C（空格分隔）。
  多选题最少选2个，绝不要只输出1个字母。如果题目中明确标有"多选题"字样，必须输出2个或以上选项。
- 必须回答题目列表中的每一道题，不可跳过。如果某题不确定，请写"不确定"
- 禁止输出思考过程、解释或任何非答案的内容
```

---

## 已知限制

### 严重 (🔴)

1. **协程泄漏** — `ExamAccessibilityService.onDestroy()` 缺少 `scope.cancel()`，Service 重启后旧协程继续运行
2. **Native 内存泄漏** — `findAllClickable()` 和 `searchMatches()` 未调用 `child.recycle()`，违反 `AccessibilityNodeInfo` API 契约

### 警告 (⚠️)

3. **无上限 KB 累积** — `KnowledgeBaseManager` 的 `mutableListOf<KBEntry>()` 无上限保护，持续导入可能导致 OOM
4. **全量 JSON 序列化** — 每次变更都完全重写 `kb_data.json`，频繁修改时 IO 抖动
5. **OnTouchListener 泄漏风险** — `SidebarService` SAM 捕获 Service 引用，异常销毁路径可能泄漏
6. **POI 双重内存** — `importExcelWithDedup()` 中 `readBytes()` + POI 同时驻留

### 提示 (ℹ️)

7. **L2 O(n) 扫描** — `getAll()` + 逐条 trigram，>1000 WikiPage 时延迟线性增长到 50+ ms
8. **API Key 硬编码** — `AppConfig.kt` 中默认端点、Key 源码可见
9. **LLMClient 非单例** — 每次 `solve()` 新建 OkHttpClient，连接池不共享
10. **L2 未接入管道** — `tryWikiMatchAll()` 已实现，但 `solve()` 从 L1 直接跳到 L3/L4
11. **PDF 提取依赖** — PDFBox Android 在某些 PDF 文件可能提取失败
12. **首次 ETA 不准** — 依赖估算（length/1.5），无历史数据校准
13. **无 instrumentation 测试** — 无法在真实设备上自动测试（受限于无 mockable 的 android.jar）

---

## 性能概要

| 场景 | 延迟 | 说明 |
|:---|:---|:---|
| L1 命中 | ~1-3ms | 纯 CPU，无 IO |
| L2 命中 | ~10-25ms | DB + trigram 全表 |
| L3 搜索 | 1-7s | 网络 I/O |
| L4 LLM | 5-30s | 网络 I/O + 推理 |
| 平均总延迟 | 4-25s | 含 35% 提前返回 |
| | 6-37s | 纯 L3+L4 路径 |

| 导入场景 | 1k 行 | 10k 行 | 50k 行 |
|:---|:---|:---|:---|
| 处理时间 | ~80-200ms | ~350-1300ms | ~2-6s |
| 峰值内存 | ~5-10MB | ~10-25MB | ~30-80MB |

详见 `PERFORMANCE_REPORT.md`。

---

## 版本历史

| 版本 | 日期 | 关键变更 |
|:---|:---|:---|
| **3.1.0** | 2026-06 | ProGuard 优化、判断题空题干跳过选项救援、release 签名 |
| **3.0.0** | 2026-06 | 底部导航重构（Home/题库/知识库/设置）、Wiki 页面详情、设计系统升级 |
| **2.x** | — | Wiki KB（Room + FTS4 + Embedding）、PPT/PDF 导入、 | Zip 批量导入 |
| **1.1.0** | 2026-06 | L1 混合评分重构（Trigram+Bigram+Token+选项+LCS）、冲突消解、空题干保护、自动列检测 |
| **1.0.0** | 2026-06 | 初始版本 |
