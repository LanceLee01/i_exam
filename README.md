# 考试助手 (ExamHelper)

> **i国网** 考试辅助工具 — Android 无障碍读屏 + 本地大模型实时解答 + 自动点击 + 知识库管理

## 目录

- [功能概览](#功能概览)
- [系统要求](#系统要求)
- [快速开始](#快速开始)
- [使用手册](#使用手册)
- [知识库系统](#知识库系统)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [LLM 配置](#llm-配置)
- [开发指南](#开发指南)

---

## 功能概览

| 功能 | 说明 |
|:---|:---|
| **屏幕读取** | 通过 AccessibilityService 遍历 i国网 界面节点树，提取题目文字 |
| **水印过滤** | 自动过滤"非涉密平台""严禁处理"等干扰水印 |
| **4 级答题流水线** | 题库精准匹配 → 知识库检索 → KB 推断 → AI 直接答题 |
| **流式解答** | SSE 流式接收 LLM 回复，实时显示在悬浮侧边栏 |
| **自动点击** | 解析 LLM 答案（A-F），通过 Accessibility 手势自动点击选项 |
| **知识库管理** | 支持 Excel 题库导入 + txt/md 文档 LLM 解析入库 |
| **增量去重** | SHA256 哈希缓存，避免重复导入相同文件 |

---

## 系统要求

| 项目 | 要求 |
|:---|:---|
| 操作系统 | Android 8.0 (API 26) 及以上 |
| 目标设备 | OPPO X9 Ultra (Android 15) 已测试 |
| LLM 服务 | OpenAI 协议兼容的 API，推荐本地 llama.cpp |
| 权限 | 悬浮窗权限 + 无障碍服务 + 通知权限 |

---

## 快速开始

### 1. 安装 APK

```bash
adb install -r app-debug.apk
```

### 2. 首次启动引导

打开 App 进入 4 步引导页：

1. **了解功能** — 确认应用用途
2. **开启悬浮窗** — 跳转系统设置，允许"在其他应用上层显示"
3. **开启无障碍** — 跳转系统设置 → 已安装的服务 → 「考试助手」→ 开启
4. **配置 API** — 填入 LLM 端点地址

### 3. 启动侧边栏

配置保存后，主界面出现「启动侧边栏」按钮，点击即可。

屏幕右边缘出现半透明白色指示条（4dp 宽），左滑或点击展开答题面板。

### 4. 使用考试助手

1. 打开 i国网 进入考试页面
2. 左滑展开侧边栏，点击 **「读取屏幕」**
3. 确认识别内容无误，点击 **「解答」**
4. 等待 LLM 流式输出答案
5. 点击 **「自动填入」** 将答案自动点击到考试界面

---

## 使用手册

### 侧边栏操作

```
┌─────────────────────┐
│  🤖 考试助手      ✕ │  ← 标题栏，点击 ✕ 关闭
├─────────────────────┤
│ 无障碍服务状态提示    │  ← 断开时显示红色警告
├─────────────────────┤
│   [ 📖 读取屏幕 ]    │  ← 主操作按钮
│                      │
│  ── 答案 ──          │
│  来源: 🤖 AI解答     │  ← 答案来源标注
│                      │
│  [1] A              │  ← 白色加粗的答案行
│  [2] B              │
│                      │
│  [ 🔄 重新解答 ]     │  ← 返回预览状态
│  [ 💾 保存到题库 ]   │  ← 一键存入 Excel 知识库
│  [ ✓ 自动填入 ]     │  ← 自动点击考试选项
├─────────────────────┤
│ ● 作答完成           │  ← 底部状态栏
└─────────────────────┘
```

### 侧边栏状态说明

| 状态 | 显示 | 说明 |
|:---|:---|:---|
| **Idle** | ● 空闲检测中 | 就绪，等待操作 |
| **Loading** | 旋转 + 已用秒数 + 预估总时间 | 正在调用 LLM 或读取屏幕 |
| **Preview** | 识别出的文字 + 解答按钮 | 等待用户确认 |
| **Streaming** | 白色流式文字 | LLM 实时输出答案 |
| **Done** | 答案 + 来源标注 + 操作按钮 | 答题完成 |
| **Error** | 红色错误信息 | 网络/权限/解析异常 |

### 自动点击机制

1. 解析答案中的题号和选项（如 `[1] A`、`1. B`）
2. 遍历无障碍节点树，按题号分组，匹配对应选项
3. 多选题目自动寻找「确认」按钮并点击
4. 判断题（对/错）通过点击判断指示器完成
5. 每道题间隔 1.5 秒，每次点击随机延迟 80-300ms

### 答案来源标注

每次作答都会在答案上方显示来源：

| 标签 | 含义 | 触发条件 |
|:---|:---|:---|
| 📋 题库匹配 | Excel 题库精准命中 | Jaccard 相似度 ≥ 70% |
| 📖 知识库匹配 | Wiki 知识库直接命中 | Jaccard 相似度 ≥ 50% |
| 📖 知识库推断 | LLM + KB 上下文推理 | Excel ≥ 40% 或 Wiki ≥ 20% |
| 🤖 AI解答 | 纯 LLM 直接作答 | 无 KB 匹配 |

---

## 知识库系统

知识库采用双层架构：

### Excel 题库（L1 精准匹配）

用于存储 **原题 → 答案** 的直接映射。

**导入方式：**
1. 左侧栏 → 设置 → 知识库管理
2. 点击知识库卡片上的 📤 图标
3. 选择 .xlsx 文件

**Excel 格式要求：**
| A 列（题目） | B 列（答案） | C 列（来源，可选） |
|:---|:---|:---|
| 电气设备发生火灾时，应首先？ | B | 2024 年题库 |

**匹配算法：** 字符级 Trigram Jaccard 相似度，≥70% 直接命中。

### Wiki 知识库（L2 语义检索）

用于存储 **概念、原理、规程** 等结构化知识，支持"题目变了但考同一知识点"的场景。

**导入方式：**
1. 知识库管理 → 点击「导入文档」
2. 选择 .txt 或 .md 文件
3. LLM 自动解析文档，生成结构化 Wiki 页面

**LLM 解析流程：**
```
原始文档 → LLM 分析 → 提取知识点 → 生成 Wiki 页（YAML 元数据 + Markdown 正文）
```

**生成格式示例：**
```markdown
---
type: procedure
title: 触电急救基本步骤
tags: 安全, 触电, 急救
summary: 发现有人触电时应立即采取的措施和急救步骤
---

## 概述
发现有人触电时，首先应确保自身安全...

## 详细内容
1. 立即切断电源，若无法切断则用干燥绝缘物分离触电者
2. 判断意识和呼吸，无反应立即拨打120
3. 进行心肺复苏（CPR）直至专业救援到达

## 相关概念
- [[绝缘防护]] — 预防触电的关键措施
- [[安全用电规程]] — 日常工作的安全要求
```

**存储方案：** Room 数据库 + SQLite FTS4 全文索引

**搜索算法：** FTS4 全文搜索 + Trigram Jaccard 混合检索

### 真题入库

每次 AI 答题完成后，可点击 **「保存到题库」** 按钮，将当前 (题目, 答案) 自动存入激活的知识库。下次遇到相同题目时 L1 直接命中，无需再调 LLM。

### 增量去重

- **Excel 导入：** 基于文件 SHA256 哈希，已导入过的文件自动跳过
- **文档导入：** 基于 Room SourceFile 表记录，内容未变化则跳过

### 多知识库管理

支持创建多个知识库（如"电工基础""安全规程"），可随时切换激活。

---

## 技术架构

```
┌─────────────────────────────────────────────────┐
│                   Android App                    │
├─────────────────────────────────────────────────┤
│  MainActivity                                    │
│  ├── WelcomeScreen      (引导页)                  │
│  ├── SettingsScreen     (API 配置 + KB 入口)       │
│  └── KnowledgeBaseScreen (KB 管理)                │
├─────────────────────────────────────────────────┤
│  SidebarService          (前台服务)                │
│  ├── EdgeHandle          (边栏指示条)              │
│  └── SidebarPanel        (答题面板 UI)             │
│       └── SolvePipeline  (4级流水线)               │
├─────────────────────────────────────────────────┤
│  ExamAccessibilityService (无障碍服务)              │
│  ├── 屏幕文字提取 (节点树遍历)                      │
│  ├── 水印过滤 (WatermarkFilter)                    │
│  └── 自动点击 (AccessibilityAction)                │
├─────────────────────────────────────────────────┤
│  数据层                                          │
│  ├── AppConfig (DataStore 配置)                    │
│  ├── KnowledgeBaseManager (Excel KB JSON 存储)      │
│  └── AppDatabase (Room + FTS4 Wiki KB 存储)        │
│       ├── WikiPage / WikiPageFts                   │
│       ├── Wikilink                                 │
│       └── SourceFile                               │
├─────────────────────────────────────────────────┤
│  网络层                                          │
│  └── LLMClient (OkHttp SSE 流式)                   │
├─────────────────────────────────────────────────┤
│  通信总线                                         │
│  └── ExtractedTextBus (StateFlow 事件总线)          │
└─────────────────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术 | 说明 |
|:---|:---|:---|
| 语言 | Kotlin 2.0.21 | |
| UI | Jetpack Compose + Material 3 | BOM 2024.09 |
| 构建 | Gradle KTS | AGP 8.7.3, KSP 编译 |
| 数据库 | Room 2.6.1 + SQLite FTS4 | Wiki 知识库 |
| 存储 | DataStore Preferences | 应用配置 |
| 存储 | JSON (Gson) | Excel 题库序列化 |
| 网络 | OkHttp 4.12.0 + SSE | LLM 流式调用 |
| 文档解析 | Apache POI 5.2.5 | Excel 读取 |
| 架构模式 | 单 Activity + Compose 导航 | |
| 通信 | StateFlow / SharedFlow 事件总线 | 替代 LocalBroadcast |
| 生命周期 | 自定义 WindowLifecycleOwner | 悬浮窗 Compose 生命周期 |

### 数据流（答题流程）

```
用户点击「解答」
  → SolvePipeline.solve(text)
    ├── L1: KnowledgeBaseManager.search() → ≥70% → Done(EXCEL_MATCH)
    ├── L2: KBEngine.searchByQuestion()   → ≥50% → Done(KB_MATCH)
    ├── L3: 构建 KB 上下文 prompt        → LLM.chatStream() → Done(KB_INFER)
    └── L4: 纯 user prompt              → LLM.chatStream() → Done(LLM_DIRECT)
  → ExtractedTextBus.updateSidebarState()
  → SidebarPanel 重组渲染
```

### 无障碍服务配置

```xml
accessibilityEventTypes: 窗口内容变化、状态变化、滚动、焦点
accessibilityFlags: 交互窗口、View ID、非重要节点
canRetrieveWindowContent: true
canPerformGestures: true  (用于自动点击)
```

### 悬浮窗实现

- `TYPE_APPLICATION_OVERLAY` 窗口
- `FLAG_NOT_FOCUSABLE` — 不抢焦，不影响考试操作
- `FLAG_NOT_TOUCH_MODAL` — 触控穿透
- 边栏指示条 24dp 宽（4dp 可见 + 20dp 触摸区域）
- 面板占屏幕 65% 宽 × 80% 高，右对齐
- 右滑 25% 宽度收起面板

### 水印过滤

i国网考试页面嵌有安全水印文字，包含"非涉密平台""严禁处理"等关键字。提取屏幕文字时自动过滤这些干扰内容。关键字可在代码中配置。

---

## 项目结构

```
app/src/main/java/com/examhelper/app/
├── MainActivity.kt                    # 单 Activity 入口
├── ExamApplication.kt                 # Application，初始化 DB + KB
├── data/
│   └── AppConfig.kt                   # DataStore 配置持久化
├── filter/
│   └── WatermarkFilter.kt             # 屏幕水印过滤
├── knowledge/
│   ├── KBEngine.kt                    # Wiki 知识库引擎（导入/搜索/管理）
│   ├── KnowledgeBaseManager.kt        # Excel 题库管理器（JSON 持久化）
│   └── db/
│       ├── AppDatabase.kt             # Room 数据库单例
│       ├── WikiPage.kt                # WikiPage 实体 + FTS4 索引
│       ├── WikiPageDao.kt             # WikiPage DAO (CRUD + FTS 搜索)
│       ├── Wikilink.kt                # 页面交叉引用实体
│       ├── WikilinkDao.kt             # Wikilink DAO
│       ├── SourceFile.kt              # 源文件记录实体
│       └── SourceFileDao.kt           # SourceFile DAO
├── network/
│   └── LLMClient.kt                   # OpenAI 协议 LLM 客户端 (SSE 流式)
├── pipeline/
│   └── SolvePipeline.kt               # 4 级答题流水线
├── service/
│   ├── SidebarService.kt              # 悬浮侧边栏前台服务
│   ├── ExamAccessibilityService.kt    # 无障碍服务（读屏 + 自动点击）
│   └── WindowLifecycleOwner.kt        # 悬浮窗 Compose 生命周期管理
├── ui/
│   ├── sidebar/
│   │   ├── EdgeHandle.kt              # 右边缘指示条 UI
│   │   └── SidebarPanel.kt            # 答题面板 UI（含所有状态渲染）
│   ├── screen/
│   │   ├── WelcomeScreen.kt           # 4 步引导页
│   │   ├── SettingsScreen.kt          # API 配置页 + KB 入口
│   │   └── KnowledgeBaseScreen.kt     # 知识库管理页
│   └── theme/
│       ├── Theme.kt                   # Compose Material 3 主题
│       └── Type.kt                    # 字体排印
└── util/
    └── ExtractedTextBus.kt            # 事件总线 + SidebarState 定义
```

---

## LLM 配置

### 本地部署（推荐）

```bash
# llama.cpp 启动命令
./llama-cli \
  --model Qwen3-27B-Q4_K_M.gguf \
  --host 0.0.0.0 \
  --port 8082 \
  --ctx-size 4096 \
  --threads 16
```

App 端配置：
- API 端点：`http://192.168.18.28:8082`
- 模型名称：留空（自动使用服务端默认模型）
- API Key：留空（本地服务无需认证）

### 云端 API

支持任何 OpenAI 协议兼容的服务：
- DeepSeek：`https://api.deepseek.com`
- OpenAI：`https://api.openai.com/v1`
- 其他兼容服务

### System Prompt

默认 prompt 为：

```
你是考试答题助手。请认真阅读以下从考试界面提取的文字，其中可能包含水印等无关信息，请忽略它们。
直接给出答案，不要思考过程。识别出题目后，只输出题号和答案选项，不要解释理由。每行一题。格式严格如下：

[题号] 答案选项

如果某题不确定，请如实写"不确定"。
```

可在代码 `AppConfig.kt` 中修改。

### LLM 流式协议

- 使用 `chat/completions` 端点，`stream: true`
- 支持 `reasoning_content` 字段（DeepSeek/通义千问的 think 模式）
- `reasoning_content` 被缓冲但不在流式过程中显示，仅当 `content` 为空时作为兜底
- 客户端估算 prompt tokens（文本长度 / 1.5），不调 `/v1/tokenize`

---

## 开发指南

### 编译

```bash
# 要求：Java 17+, Android SDK 35+
export JAVA_HOME="/path/to/jdk"
./gradlew :app:assembleDebug
```

### 关键配置

| 配置项 | 位置 | 默认值 |
|:---|:---|:---|
| LLM 端点 | 设置页 / AppConfig | `https://api.deepseek.com` |
| 模型名称 | 设置页 / AppConfig | `deepseek-chat` |
| Temperature | 设置页 / AppConfig | 0.3 |
| Max Tokens | 设置页 / AppConfig | 2048 |
| 知识库匹配阈值 (L1) | SidebarPanel | 70% |
| 知识库匹配阈值 (L2) | SidebarPanel | 50% |
| 知识库推断阈值 | SidebarPanel | 40% / 20% |
| 侧边栏宽度 | SidebarService | 65% 屏幕宽度 |
| 文档导入上限 | KBEngine | 8000 字符 |
| ETA 默认速度 | SidebarPanel | 35 tok/s |

### 添加新 LLM 提供商

在 `LLMClient.buildUrl()` 中处理 URL 模式即可。只要服务支持 OpenAI 协议 `/v1/chat/completions` 端点，无需修改代码。

### 添加新文件类型支持

在 `KBEngine.importFile()` 中扩展文件读取逻辑，添加对应的 MIME 类型即可。

---

## 已知限制

1. i国网 无 ROOT 权限下无法截图
2. FLAG_SECURE 窗口无法通过截图方案获取内容
3. 部分机型需手动在系统设置中开启无障碍服务
4. LLM 答案格式不匹配时，自动点击可能失败
5. ETA 预估依赖历史请求的 token 速度，首次使用不准
6. 文档超过 8000 字符会被截断

---

## 版本历史

| 版本 | 日期 | 说明 |
|:---|:---|:---|
| 1.0.0 | 2026-06-14 | 初始版本：无障碍读屏、流式解答、自动点击、知识库管理 |
