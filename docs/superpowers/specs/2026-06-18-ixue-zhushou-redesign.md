# i学助手 App 整体重构设计文档

> 设计日期: 2026-06-18
> 状态: 已定稿

---

## 1. 项目概述

### 1.1 基本信息

| 项目 | 值 |
|------|-----|
| App 名称 | **i学助手** (iStudy Assistant) |
| 标语 | 学海无涯，有我做舟 |
| 当前名称 | 考试助手 (ExamHelper) |
| 平台 | Android 8.0+ |
| 技术栈 | Kotlin 2.0.21, Jetpack Compose + M3 |

### 1.2 设计目标

- 从当前简单状态导航升级为底部导航栏多 Tab 架构
- 全面提升 UI 视觉质感，采用暖橙白 SaaS 极简风格
- 增加暗色模式切换（紫蓝暗色）
- 统一品牌形象：新名称、新 Logo、新标语

---

## 2. 配色方案

### 2.1 默认亮色：G · 暖橙白

```
背景色    #FDFCFB  暖白/米白
卡片色    #FFFFFF  纯白
主色      #F27A3E  活力橙（强调色）
深橙      #D6652D  按钮 hover/按下
淡橙      #FFF8F4  标签/背景区
主文字    #1C1C1E  近黑
次要文字  #8E8E93  中灰
成功绿    #10B981  成功/正确状态
```

### 2.2 暗色切换：A · 紫蓝暗色

```
背景色    #0F0F1A  深蓝紫
卡片色    rgba(255,255,255,0.06)  半透白
主色      #7C3AED → #2563EB  紫蓝渐变
深紫      #6D28D9
深蓝      #1E40AF
主文字    #FFFFFF  白色
次要文字  rgba(255,255,255,0.5)
成功绿    #10B981
```

### 2.3 系统主题切换

- 默认使用 G · 暖橙白（亮色）
- 首页右上角提供 🌙 按钮一键切换到 A · 紫蓝暗色
- 切换状态持久化到 AppConfig

---

## 3. Logo 设计

### 3.1 图标

- **造型**: 极简字母 "i"（竖线 + 顶部圆点）+ 底部平台线 + 斜线 X 型学习暗示
- **背景**: 圆角矩形 24dp, 96x96dp
- **配色**: 暖橙渐变 `linear-gradient(135deg, #F27A3E, #D6652D)`
- **前景色**: 白色 SVG，无衬线

### 3.2 文字标识

- 中文: **i学助手**（i 小写 + 学助手，字重 Bold）
- 英文: **iStudy Assistant**（字重 Regular, 全大写, 字距 0.08em, 次要文字色）

---

## 4. 导航架构

### 4.1 整体变更

```
当前:
  MainActivity
   ├─ !setupComplete → WelcomeScreen
   ├─ showKB → KnowledgeBaseScreen
   └─ else → SettingsScreen

新:
  MainActivity
   ├─ !setupComplete → WelcomeScreen (保留现有引导)
   └─ MainScreen
        ├─ 底部导航栏 (5 项含居中 FAB)
        ├─ Tab 1: 首页  (HomeTab.kt)
        ├─ Tab 2: 题库  (QuestionBankTab.kt)
        ├─ Tab 3: 知识库 (KnowledgeBaseTab.kt)
        ├─ Tab 4: 设置  (SettingsTab.kt)
        └─ Start FAB → 启动 i国网 (居中)
```

### 4.2 底部导航栏样式

| 属性 | 值 |
|------|-----|
| 形状 | 悬浮式大圆角 24dp |
| 背景 | `rgba(255,255,255,0.92)` + `backdrop-filter: blur(24px)` 毛玻璃 |
| 边框 | 1px `rgba(0,0,0,0.04)` |
| 阴影 | `0 4px 24px rgba(0,0,0,0.06)` |
| 内发光 | `inset 0 1px 0 rgba(255,255,255,0.8)` |

### 4.3 导航项

| 位置 | 名称 | 图标 | 说明 |
|------|------|------|------|
| 左 1 | 首页 | Home 图标 | 暖橙高亮选中 |
| 左 2 | 题库 | Book 图标 | 未选中半透明 |
| **居中** | **Start** | **文字** | **52dp 暖橙渐变圆形凸起，一键打开 i国网** |
| 右 2 | 知识库 | Database 图标 | 未选中半透明 |
| 右 1 | 设置 | Settings 图标 | 未选中半透明 |

---

## 5. 各 Tab 设计

### 5.1 首页 Tab (HomeTab.kt)

**文件**: 新增 `ui/screen/HomeTab.kt`

**布局从上到下**:

1. **品牌头部**
   - 左侧: 圆形 Logo + "i学助手" / "学海无涯，有我做舟"
   - 右侧: 🌙 主题切换按钮（图标: 月亮）

2. **搜索框**
   - 白色圆角输入框，前置 🔍 图标
   - 搜索范围：Excel 题库 (KnowledgeBaseManager)
   - 右侧橙色 [搜索] 按钮
   - 点击搜索跳转到题库 Tab 并显示结果

3. **功能概览区**（待后续迭代填充）
   - 从 README 提取 4 个核心功能点
   - 列表卡片展示，每项含图标 + 标题 + 简要说明

4. **底部导航**（全局组件，不属于 Tab 内容）

### 5.2 题库 Tab (QuestionBankTab.kt)

**文件**: 新增 `ui/screen/QuestionBankTab.kt`

**功能**:
- 概览卡片：展示总题目数、激活知识库、最近导入
- 搜索/筛选栏：按题目文本搜索，按题型筛选
- 题目列表 LazyColumn：题号 + 题目 + 答案 + 题型标签
- 导入操作栏：导入 Excel / ZIP
- 知识库管理入口 → 切换到 Tab 3

**数据来源**: `KnowledgeBaseManager` (Excel KB)

### 5.3 知识库 Tab (KnowledgeBaseTab.kt)

**复用现有**: `KnowledgeBaseScreen.kt` 核心内容

**调整点**:
- 去掉独立 TopAppBar 返回按钮，嵌入 Tab 布局
- 搜索框搜索 Wiki 知识库内容
- 保留：知识库列表、新建/删除、Excel/ZIP 导入、KbDetailScreen 入口

**数据来源**: `KBEngine` (Room DB, Wiki pages)

### 5.4 设置 Tab (SettingsTab.kt)

**复用现有**: `SettingsScreen.kt` 核心内容

**调整点**:
- 设置项分组排版（分组标题）
- 系统提示词改为可折叠区域
- Tab 内嵌，去掉独立 TopAppBar
- 保存按钮 + 即时反馈

**分组**:
| 分组 | 内容 |
|------|------|
| LLM 配置 | API 端点、API Key、模型名称、Temperature、Max Tokens |
| 联网搜索 | Tavily API Key |
| 高级设置 | 系统提示词（可折叠） |

---

## 6. FAB (Start 按钮)

### 6.1 位置

底部导航栏正中央，在"题库"和"知识库"之间。

### 6.2 样式

| 属性 | 值 |
|------|-----|
| 直径 | 52dp |
| 形状 | 正圆形 |
| 背景 | `linear-gradient(135deg, #F27A3E, #D6652D)` 暖橙渐变 |
| 阴影 | `0 4px 12px rgba(242,122,62,0.25)` |
| 文字 | "Start" 白色，粗体，字距 0.06em，大写 |
| 高度 | 高出导航栏表面约 6dp |

### 6.3 功能

- **点击**: `packageManager.getLaunchIntentForPackage("com.sgcc.iguowang")` → 启动 i国网
- **同时**: `moveTaskToBack(true)` 最小化 i学助手
- **未安装**: Toast 提示 "请先安装 i国网 App"
- **长按**: Toast "一键打开 i国网"

---

## 7. 文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `ui/screen/MainScreen.kt` | 底部导航容器 + FAB，承载 4 个 Tab |
| `ui/screen/HomeTab.kt` | 首页 Tab：品牌头 + 搜索框 + 功能概览 |
| `ui/screen/QuestionBankTab.kt` | 题库 Tab：概览 + 列表 + 搜索筛选 |
| `ui/screen/SettingsTab.kt` | 设置 Tab（包装 SettingsScreen） |
| `ui/screen/KnowledgeBaseTab.kt` | 知识库 Tab（包装 KnowledgeBaseScreen） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `MainActivity.kt` | 简化导航逻辑：`!setupComplete` → WelcomeScreen, else → MainScreen |
| `ui/theme/Theme.kt` | 扩展 ExamHelperColors，增加暖橙白配色、品牌色 |
| `ExamApplication.kt` | 应用名称更新为 i学助手（AndroidManifest） |

### 无变更

| 文件 | 原因 |
|------|------|
| `WelcomeScreen.kt` | 保留首次引导流程 |
| `SettingsScreen.kt` | 内容被 SettingsTab 复用，不改原文件 |
| `KnowledgeBaseScreen.kt` | 内容被 KnowledgeBaseTab 复用，不改原文件 |
| `KbDetailScreen.kt` | 功能无影响 |
| 所有 service / pipeline / data 层 | 仅 UI 层变更，不影响业务逻辑 |

---

## 8. 实施阶段

### Phase 1: 基础设施
1. 更新 `Theme.kt` 色彩系统（暖橙白 + 紫蓝暗色）
2. 新建 `MainScreen.kt`：Scaffold + 底部导航 + Start FAB
3. 修改 `MainActivity.kt`：简化导航逻辑

### Phase 2: Tab 迁移
4. 新建 `SettingsTab.kt`：包装现有设置内容 + 分组优化
5. 新建 `KnowledgeBaseTab.kt`：包装现有知识库内容
6. 新建 `QuestionBankTab.kt`：题库概览 + 列表

### Phase 3: 首页与品牌
7. 新建 `HomeTab.kt`：品牌头 + 搜索框 + 功能概览
8. 实现 Logo 图标资源（SVG → VectorDrawable）
9. 更新 App 名称为 i学助手
10. 实现 Start FAB 一键启动 i国网

### Phase 4: 打磨
11. 主题切换逻辑（暖橙白 ↔ 紫蓝暗色）
12. 玻璃质感导航栏细节调优
13. Tab 切换动画
14. 测试 & bug 修复

---

## 9. 设计原则

- **形状统一**: 所有卡片/按钮/输入框统一大圆角（12-16dp）
- **色彩克制**: 单一强调色（暖橙 G / 紫蓝 A），不泛滥
- **玻璃质感**: 导航栏毛玻璃 + 内发光边框
- **间距呼吸**: 信息密度适中，留白充分
- **微交互**: Spring 物理动画，按压回弹
- **一致性**: 全局圆角系统、字体、间距、色彩语义统一
