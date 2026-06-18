# i学助手 App 整体重构设计文档

> 设计日期: 2026-06-18
> 状态: 已定稿
> 版本: v2.0 (详细版)

---

## 1. 项目概述

### 1.1 基本信息

| 项目 | 值 |
|------|-----|
| App 名称 | **i学助手** (iStudy Assistant) |
| 标语 | 学海无涯，有我做舟 |
| 当前名称 | 考试助手 (ExamHelper) |
| 包名 | com.examhelper.app (不变) |
| 平台 | Android 8.0+ (API 26+) |
| 技术栈 | Kotlin 2.0.21, Jetpack Compose + M3, KSP, Room |

### 1.2 设计目标

- 从当前简单状态导航升级为底部导航栏多 Tab 架构
- 全面提升 UI 视觉质感，采用暖橙白 SaaS 极简风格
- 增加暗色模式切换（紫蓝暗色）
- 统一品牌形象：新名称、新 Logo、新标语

### 1.3 设计三轴

| 轴 | 值 | 说明 |
|----|------|------|
| DESIGN_VARIANCE | 6 | Offset 不对称布局，有秩序不呆板 |
| MOTION_INTENSITY | 4 | 流畅过渡动画，不花哨 |
| VISUAL_DENSITY | 4 | 信息密度适中，留白充分 |

---

## 2. 配色方案

### 2.1 默认亮色：G · 暖橙白

```
背景色      #FDFCFB    暖白/米白
卡片色      #FFFFFF    纯白
主色        #F27A3E    活力橙（单一强调色）
深橙        #D6652D    按钮 hover/按下态
淡橙        #FFF8F4    标签背景/卡片区底色
淡橙边框    rgba(242,122,62,0.12)
主文字      #1C1C1E    近黑
次要文字    #8E8E93    中灰
占位文字    rgba(0,0,0,0.25)
卡片边框    rgba(0,0,0,0.04)
输入框边框  rgba(0,0,0,0.06)
成功绿      #10B981    成功/正确状态
错误红      #EF4444    错误状态
警告黄      #F59E0B    警告状态
```

**渐变**: 按钮/FAB `linear-gradient(135deg, #F27A3E, #D6652D)`

### 2.2 暗色切换：A · 紫蓝暗色

```
背景色      #0F0F1A    深蓝紫
卡片色      rgba(255,255,255,0.06)
主色        #7C3AED → #2563EB    紫蓝渐变
深紫        #6D28D9
深蓝        #1E40AF
主文字      #FFFFFF    白色
次要文字    rgba(255,255,255,0.5)
占位文字    rgba(255,255,255,0.25)
卡片边框    rgba(255,255,255,0.06)
输入框边框  rgba(255,255,255,0.1)
成功绿      #10B981
错误红      #EF4444
警告黄      #F59E0B
```

**渐变**: 按钮/FAB `linear-gradient(135deg, #7C3AED, #2563EB)`

### 2.3 主题切换

- **默认**: G · 暖橙白（亮色）
- **切换入口**: 首页 Tab 右上角圆形按钮（月亮/太阳图标切换）
- **持久化**: 保存到 `AppConfig.isDarkMode` (DataStore)
- **实现方式**: `ExamHelperTheme` 接收 `darkTheme` 参数，从 AppConfig 读取
- **切换动画**: Compose `AnimatedContent` 或 `Crossfade` 过渡
- **Android 系统联动**: 可选跟随系统 `isSystemInDarkTheme()`

```kotlin
// AppConfig 新增
var isDarkMode: Boolean = false
fun setIsDarkMode(dark: Boolean)
fun getIsDarkMode(): Flow<Boolean>

// ExamHelperTheme 改造
@Composable
fun ExamHelperTheme(
    darkTheme: Boolean = false, // false=暖橙白, true=紫蓝暗色
    content: @Composable () -> Unit
)
```

### 2.4 Theme.kt 代码变更

```kotlin
object ExamHelperColors {
    // === 暖橙白 (G) ===
    object WarmOrange {
        val Primary = Color(0xFFF27A3E)
        val PrimaryDark = Color(0xFFD6652D)
        val Surface = Color(0xFFFDFCFB)
        val SurfaceCard = Color(0xFFFFFFFF)
        val SurfaceAccent = Color(0xFFFFF8F4)
        val OnSurface = Color(0xFF1C1C1E)
        val OnSurfaceSecondary = Color(0xFF8E8E93)
        val Outline = Color(0x0A000000)
        val OutlineInput = Color(0x0F000000)
    }

    // === 紫蓝暗色 (A) ===
    object VioletDark {
        val Primary = Color(0xFF7C3AED)
        val PrimaryVariant = Color(0xFF2563EB)
        val Surface = Color(0xFF0F0F1A)
        val SurfaceCard = Color(0x0FFFFFFF)
        val OnSurface = Color.White
        val OnSurfaceSecondary = Color(0x80FFFFFF)
        val Outline = Color(0x0FFFFFFF)
        val OutlineInput = Color(0x1AFFFFFF)
    }

    // 通用语义色
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
}
```

---

## 3. Logo 设计

### 3.1 图标规格 (Android Adaptive Icon)

| 属性 | 值 |
|------|-----|
| 造型 | 极简字母 "i"（竖线 + 顶部圆点）+ 底部平台线 + 斜线学习暗示 |
| 背景层 | 暖橙渐变 `#F27A3E → #D6652D`，24dp 圆角矩形 |
| 前景层 | 白色矢量图形 |
| 输出格式 | VectorDrawable (XML) |
| 尺寸 | 108dp × 108dp (adaptive-icon), 96dp × 96dp (内嵌) |

### 3.2 前景 SVG 路径描述

```
竖线:    M26,20 L26,40 (strokeWidth=5, round cap)
圆点:    cx=26, cy=11, r=5 (fill)
平台线:  M14,46 L38,46 (strokeWidth=3, round cap, alpha=0.5)
斜线:    M18,38 L34,26 (strokeWidth=2.5, round cap, alpha=0.35)
```

### 3.3 文字标识

| 元素 | 字体 | 字重 | 字号 | 颜色 |
|------|------|------|------|------|
| "i学助手" | system | Bold | 20sp | #1C1C1E |
| "学海无涯，有我做舟" | system | Regular | 11sp | #8E8E93 |
| "iStudy Assistant" | system | Regular | 11sp, 字距0.08em | #8E8E93 |

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
   ├─ !setupComplete → WelcomeScreen (引导流程不变)
   └─ else → MainScreen
        ├─ 底部导航栏 (5 项含居中 Start FAB)
        ├─ selectedTab (0-3) 切换 4 个 Tab
        │   ├─ 0: HomeTab
        │   ├─ 1: QuestionBankTab
        │   ├─ 2: KnowledgeBaseTab
        │   └─ 3: SettingsTab
        └─ Start FAB (导航栏居中，始终可见)
```

### 4.2 MainActivity 改造

```kotlin
// 旧逻辑
var showKB by remember { mutableStateOf(false) }
when {
    !setupComplete -> WelcomeScreen(...)
    showKB -> KnowledgeBaseScreen(onBack = { showKB = false })
    else -> SettingsScreen(onBack = { finish() }, onOpenKB = { showKB = true })
}

// 新逻辑
when {
    !setupComplete -> WelcomeScreen(onGetStarted = { handleSetupComplete() })
    else -> MainScreen()
}
```

### 4.3 MainScreen 状态管理

```kotlin
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isDarkMode by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = if (isDarkMode) Color(0xFF0F0F1A) else Color(0xFFFDFCFB)
    ) {
        // Tab 内容区
        when (selectedTab) {
            0 -> HomeTab(
                isDarkMode = isDarkMode,
                onToggleTheme = { isDarkMode = !isDarkMode },
                onSearch = { /* 切换到题库 Tab 并搜索 */ },
                onNavigateToTab = { selectedTab = it }
            )
            1 -> QuestionBankTab()
            2 -> KnowledgeBaseTab()
            3 -> SettingsTab()
        }

        // 底部导航栏
        GlassBottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            isDarkMode = isDarkMode
        )
    }
}
```

### 4.4 导航项定义

```kotlin
enum class BottomNavItem(
    val label: String,
    val contentDescription: String,
) {
    HOME("首页", "首页"),
    QUESTION_BANK("题库", "题库"),
    FAB("Start", "打开 i国网"),  // 居中特殊处理
    KNOWLEDGE_BASE("知识库", "知识库"),
    SETTINGS("设置", "设置"),
}
```

### 4.5 底部导航栏尺寸

| 属性 | 亮色 (G) | 暗色 (A) |
|------|----------|----------|
| 容器背景 | `rgba(255,255,255,0.92)` | `rgba(18,18,34,0.92)` |
| backdrop-filter | `blur(24px) saturate(180%)` | 同左 |
| 边框 | `1px rgba(0,0,0,0.04)` | `1px rgba(255,255,255,0.06)` |
| 阴影 | `0 4px 24px rgba(0,0,0,0.06)` | `0 8px 32px rgba(0,0,0,0.4)` |
| 内发光 | `inset 0 1px 0 rgba(255,255,255,0.8)` | `inset 0 1px 0 rgba(255,255,255,0.08)` |
| 圆角 | 24dp | 24dp |

### 4.6 选中状态指示器

- 选中 Tab：顶部 3dp 高 x 32dp 宽的圆角色条
- 亮色：`#F27A3E`（暖橙）
- 暗色：`linear-gradient(90deg, #7C3AED, #2563EB)`（紫蓝渐变）

---

## 5. 各 Tab 详细设计

### 5.1 首页 Tab (HomeTab.kt)

**文件**: 新增 `app/src/main/java/com/examhelper/app/ui/screen/HomeTab.kt`

**状态**:
```kotlin
data class HomeTabState(
    val isDarkMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<KBEntry> = emptyList(),
    val isSearching: Boolean = false
)
```

**布局规格**:

| 区域 | 间距 | 说明 |
|------|------|------|
| 状态栏 | 4dp top padding | 9:41 / 信号 |
| 品牌头部 | 12dp bottom margin | Logo + 名称 + 主题切换 |
| 搜索框 | 24dp bottom margin | 14dp 圆角输入框 |
| 功能概览 | 20dp bottom margin | 列表卡片 |
| 底部导航 | 全局组件 | 24dp 圆角浮层 |

**品牌头部尺寸**:
- Logo: 44dp × 44dp, 圆角 13dp
- 标题: 20sp Bold
- 标语: 11sp Regular, #8E8E93
- 主题切换按钮: 36dp × 36dp, 圆角 10dp

**搜索框尺寸**:
- 高度: 48dp
- 圆角: 14dp
- 内边距: 12dp 16dp
- 搜索按钮: 30dp 高, 圆角 8dp

**功能概览卡片尺寸**:
- 高度: 56dp
- 圆角: 12dp
- 图标区: 32dp × 32dp, 圆角 8dp
- 间距: 10dp gap

### 5.2 题库 Tab (QuestionBankTab.kt)

**文件**: 新增 `app/src/main/java/com/examhelper/app/ui/screen/QuestionBankTab.kt`

**状态**:
```kotlin
data class QuestionBankState(
    val totalQuestions: Int = 0,
    val activeKBName: String = "无",
    val questions: List<KnowledgeBase> = emptyList(),
    val searchQuery: String = "",
    val filterType: QuestionType? = null,  // 单选/多选/判断
    val isLoading: Boolean = false,
)
```

**布局从上到下**:

1. **概览卡片**
   - 总题目数（大数字展示）
   - 激活知识库名称
   - 最近导入时间
   - 圆角 16dp，暖白背景

2. **搜索/筛选栏**
   - 搜索输入框（同首页风格）
   - 题型筛选 Chip: 全部/单选/多选/判断
   - 筛选使用 `FilterChip` 或自定义

3. **题目列表**
   - `LazyColumn`
   - 每项: 题号标签 + 题目文本(最多2行截断) + 答案 + 题型标签
   - 点击 → 进入详情弹窗或展开
   - 圆角 12dp，间隙 8dp

4. **底部操作栏**
   - "导入 Excel" 按钮 (暖橙)
   - "导入 ZIP" 按钮 (暖橙 outline)
   - "管理知识库" 文字链接 → 切换到 Tab 3

### 5.3 知识库 Tab (KnowledgeBaseTab.kt)

**文件**: 新增 `app/src/main/java/com/examhelper/app/ui/screen/KnowledgeBaseTab.kt`

**复用逻辑**:
```kotlin
@Composable
fun KnowledgeBaseTab() {
    // 直接复用 KnowledgeBaseScreen 的核心内容
    // 但用 Tab 容器包裹，去掉独立 TopAppBar
    KnowledgeBaseContent(
        // 注入内部导航：onBack 改为空操作或切换 Tab 3
        onBack = { /* 当前 Tab 不需要返回操作 */ }
    )
}
```

**调整**: 抽取 `KnowledgeBaseContent` 为独立的 Composable，被 `KnowledgeBaseScreen` 和 `KnowledgeBaseTab` 共同引用。

**搜索功能**: 新增 `OutlinedTextField` 过滤 WikiPage 列表

### 5.4 设置 Tab (SettingsTab.kt)

**文件**: 新增 `app/src/main/java/com/examhelper/app/ui/screen/SettingsTab.kt`

**分组布局**:
```
┌─ LLM 配置 ─────────────────────────┐
│  API 端点 (输入框)                   │
│  API Key (密码框)                    │
│  模型名称 (输入框)                    │
│  Temperature (滑动条)                │
│  Max Tokens (数字输入框)              │
└──────────────────────────────────────┘

┌─ 联网搜索 ──────────────────────────┐
│  Tavily API Key (密码框)             │
└──────────────────────────────────────┘

┌─ 高级设置 ──────────────────────────┐
│  系统提示词 [展开 ▼] (可折叠)        │
│  (展开后显示大文本框 + 恢复默认)     │
└──────────────────────────────────────┘

        [ 保存配置 ] 按钮
```

**分组标题样式**:
- 左侧 3dp 彩色竖条 + 文字
- 亮色竖条: #F27A3E, 暗色竖条: #7C3AED

**折叠面板实现**:
```kotlin
var expanded by remember { mutableStateOf(false) }
AnimatedVisibility(visible = expanded) {
    // 系统提示词编辑区
}
```

---

## 6. Start FAB 详细设计

### 6.1 位置与样式

- 位于底部导航栏正中央，"题库"和"知识库"之间
- 高出导航栏表面 6dp
- 52dp 正圆形
- 暖橙渐变背景（暗色为紫蓝渐变）
- "Start" 白色粗体大写文字，11sp，字距 0.06em
- 外发光阴影

### 6.2 Compose 实现

```kotlin
@Composable
fun StartFab(isDarkMode: Boolean) {
    val bgColors = if (isDarkMode) {
        listOf(Color(0xFF7C3AED), Color(0xFF2563EB))
    } else {
        listOf(Color(0xFFF27A3E), Color(0xFFD6652D))
    }
    val shadowColor = if (isDarkMode) Color(0x5A7C3AED) else Color(0x40F27A3E)

    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape, spotColor = shadowColor, ambientColor = shadowColor)
            .background(
                brush = Brush.linearGradient(bgColors, start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)),
                shape = CircleShape
            )
            .clickable { launchIGuoWang() }
            .then(
                Modifier
                    .border(2.dp, Color.White.copy(alpha = 0.06f), CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Start",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.em
        )
    }
}
```

### 6.3 启动 i国网 逻辑

```kotlin
private fun launchIGuoWang(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.sgcc.iguowang")
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        // 最小化 i学助手
        (context as? Activity)?.moveTaskToBack(true)
    } else {
        Toast.makeText(context, "请先安装 i国网 App", Toast.LENGTH_SHORT).show()
    }
}
```

### 6.4 状态与反馈

| 场景 | 行为 |
|------|------|
| 正常 | 启动 i国网 + 最小化 i学助手 |
| i国网未安装 | Toast "请先安装 i国网 App" |
| 长按 | Toast "一键打开 i国网" |
| 动画 | 按压缩放 `scale(0.92)` + 透明度反馈 |

---

## 7. 圆角系统 / 形状层级

| 层级 | 圆角值 | 应用 |
|------|--------|------|
| xs | 8dp | 按钮、标签、搜索按钮、图标区 |
| sm | 10dp | 主题切换按钮 |
| md | 12-14dp | 卡片、输入框、操作按钮 |
| lg | 16-20dp | 大卡片、功能面板 |
| xl | 24dp | 底部导航栏容器 |
| pill | 999dp | 圆形按钮、FAB |

---

## 8. 间距系统

| 层级 | dp | 应用 |
|------|-----|------|
| xs | 4dp | 元素内间距 |
| sm | 8dp | 相邻元素间距 |
| md | 12dp | 卡片内边距 |
| lg | 16dp | 页面边距、卡片间距 |
| xl | 20-24dp | 区块间距 |

---

## 9. 排版系统

| 层级 | 字号 | 字重 | 颜色 | 应用 |
|------|------|------|------|------|
| H1 | 20sp | Bold | #1C1C1E | App 名称 |
| H2 | 16sp | Bold | #1C1C1E | 分组标题 |
| H3 | 15sp | SemiBold | #1C1C1E | 功能项标题 |
| Body | 14sp | Regular | #1C1C1E | 正文、输入框 |
| Body-Small | 13sp | Regular | #1C1C1E | 列表项 |
| Caption | 12sp | Regular | #8E8E93 | 次要说明 |
| Caption-Small | 11sp | Regular | #8E8E93 | 标语、时间戳 |
| Label | 10sp | Bold | accent | Start 按钮 |
| Nav | 8sp | SemiBold | accent | 导航栏文字 |

---

## 10. 图标系统

### 10.1 导航栏图标

使用 Material Icons (现有依赖不变):
- 首页: `Icons.Filled.Home`
- 题库: `Icons.AutoMirrored.Filled.List` 或 `Icons.Filled.MenuBook`
- 知识库: `Icons.Filled.Storage`
- 设置: `Icons.Filled.Settings`

### 10.2 功能图标

每个功能卡片使用对应的 Material Icons 或 Unicode Emoji（后期可替换为自定义图标）.

---

## 11. 状态管理

### 11.1 MainScreen 状态

```kotlin
class MainViewModel : ViewModel() {
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun selectTab(index: Int) { _selectedTab.value = index }
    fun toggleTheme() { _isDarkMode.value = !_isDarkMode.value }
}
```

### 11.2 主题持久化

```kotlin
// AppConfig.kt 新增
private val isDarkMode = dataStore.data.map { preferences ->
    preferences[IS_DARK_MODE_KEY] ?: false
}

suspend fun setIsDarkMode(dark: Boolean) {
    dataStore.edit { it[IS_DARK_MODE_KEY] = dark }
}
```

---

## 12. 搜索交互流程

### 12.1 首页搜索 → 题库 Tab

```
用户在首页输入搜索词
        ↓
点击搜索按钮
        ↓
MainScreen selectedTab = 1 (切换到题库 Tab)
        ↓
QuestionBankTab 接收搜索参数
        ↓
调用 KnowledgeBaseManager 搜索匹配
        ↓
显示搜索结果列表
        ↓
点击结果项 → 展开完整题目+答案
```

### 12.2 数据传递

```kotlin
// MainScreen 持有搜索状态
var globalSearchQuery by remember { mutableStateOf("") }

// 首页搜索
HomeTab(onSearch = { query ->
    globalSearchQuery = query
    selectedTab = 1  // 切换到题库
})

// 题库 Tab 接收搜索
QuestionBankTab(initialSearchQuery = globalSearchQuery)
```

---

## 13. 动画与过渡

### 13.1 Tab 切换

- 使用 `Crossfade` 过渡
- 时长: 300ms
- Easing: `FastOutSlowInEasing`

```kotlin
Crossfade(
    targetState = selectedTab,
    animationSpec = tween(300, easing = FastOutSlowInEasing)
) { tab ->
    when (tab) {
        0 -> HomeTab(...)
        1 -> QuestionBankTab(...)
        2 -> KnowledgeBaseTab(...)
        3 -> SettingsTab(...)
    }
}
```

### 13.2 主题切换

- 使用 `AnimatedContent` 或简单 `Crossfade`
- 通过 Compose 的 `colorScheme` 和 `ExamHelperColors` 自动响应

### 13.3 按钮按压反馈

```kotlin
Modifier.pointerInput(Unit) {
    detectTapGestures(
        onPress = {
            // scale 0.96, alpha 0.8
            tryAwaitRelease()
            // restore
        },
        onTap = { onClick() }
    )
}
```

---

## 14. 文件变更清单（完整版）

### 新增文件

| # | 文件路径 | 说明 |
|---|----------|------|
| 1 | `app/.../ui/screen/MainScreen.kt` | 底部导航容器 + FAB，承载 4 个 Tab 切换 |
| 2 | `app/.../ui/screen/HomeTab.kt` | 首页 Tab：品牌头 + 搜索框 + 功能概览 |
| 3 | `app/.../ui/screen/QuestionBankTab.kt` | 题库 Tab：概览 + 搜索筛选 + 题目列表 |
| 4 | `app/.../ui/screen/SettingsTab.kt` | 设置 Tab：分组设置 + 折叠提示词 |
| 5 | `app/.../ui/screen/KnowledgeBaseTab.kt` | 知识库 Tab：包装现有 KnowledgeBaseContent |
| 6 | `app/.../ui/theme/Logo.kt` | Logo 矢量图形 Composable |
| 7 | `app/.../ui/theme/BottomNavBar.kt` | 玻璃质感底部导航栏组件 |

### 修改文件

| # | 文件 | 变更内容 |
|---|------|----------|
| 1 | `MainActivity.kt` | 简化导航：`!setupComplete → WelcomeScreen, else → MainScreen` |
| 2 | `ui/theme/Theme.kt` | 扩展 ExamHelperColors：WarmOrange / VioletDark 配色对象 |
| 3 | `data/AppConfig.kt` | 新增 `isDarkMode` 持久化字段 |
| 4 | `AndroidManifest.xml` | `android:label` 改为 "i学助手" |
| 5 | `ui/theme/Type.kt` | 补充新排版层级 |
| 6 | `.gitignore` | 追加 `.superpowers/` |

### 重构文件

| # | 文件 | 变更 |
|---|------|------|
| 1 | `ui/screen/KnowledgeBaseScreen.kt` | 抽取 `KnowledgeBaseContent` 为独立 Composable，保持向后兼容 |

### 无变更（原封不动）

| 文件 | 原因 |
|------|------|
| `WelcomeScreen.kt` | 首次引导流程不变 |
| `SettingsScreen.kt` | 内容被 SettingsTab 原位引用 |
| `KbDetailScreen.kt` | 功能无影响 |
| `service/*` (全部) | 仅 UI 层变更 |
| `pipeline/*` (全部) | 仅 UI 层变更 |
| `knowledge/*` (全部) | 仅 UI 层变更 |
| `util/*` (全部) | 仅 UI 层变更 |

---

## 15. 实施阶段（详细版）

### Phase 1: 基础设施 (预估: 1-2h)

1. **更新 `.gitignore`** — 排除 `.superpowers/` ✔️ (已完成)
2. **更新 `Theme.kt`** — 添加 WarmOrange / VioletDark 配色对象
   - 修改 `ExamHelperColors`，增加两个配色命名空间
   - 修改 `ExamHelperTheme` 接受 darkTheme 参数
3. **新建 `BottomNavBar.kt`** — 玻璃质感底部导航栏组件
   - 支持 5 项布局（含居中 FAB）
   - 选中状态指示器
   - 主题自适应
4. **新建 `MainScreen.kt`** — 导航容器
   - Scaffold + selectedTab 状态 + Crossfade
   - 主题切换状态管理

### Phase 2: Tab 迁移 (预估: 1-2h)

5. **新建 `SettingsTab.kt`**
   - 从 SettingsScreen 复制核心设置内容
   - 改为没有 TopAppBar 的 Tab 布局
   - 设置项分组（分组标题 + 彩色竖条）
   - 系统提示词可折叠
6. **新建 `KnowledgeBaseTab.kt`**
   - 抽取 KnowledgeBaseScreen 核心内容为 `KnowledgeBaseContent`
   - 用 Tab 布局包装
7. **新建 `QuestionBankTab.kt`**
   - 概览卡片
   - 搜索 + 题型筛选
   - 题目列表
   - 导入操作按钮

### Phase 3: 首页与品牌 (预估: 1h)

8. **新建 `Logo.kt`** — Logo 矢量图形 Composable
   - 使用 Canvas 绘制极简 i 图标
9. **新建 `HomeTab.kt`**
   - 品牌头部（Logo + 名称 + 主题切换）
   - 搜索框（与题库 Tab 联动）
   - 功能概览区（4 个核心功能卡片）
10. **修改 `MainActivity.kt`** — 简化导航
11. **更新 `AndroidManifest.xml`** — App 名称改为 i学助手

### Phase 4: 数据层 & 交互 (预估: 1h)

12. **修改 `AppConfig.kt`** — 新增 isDarkMode 持久化
13. **实现主题切换逻辑**（首页 ↔ App 全局）
14. **实现 Start FAB 启动 i国网**
15. **实现首页搜索 → 题库 Tab 跳转**

### Phase 5: 打磨 (预估: 1h)

16. **动画调优**
    - Tab 切换 Crossfade
    - 按钮按压反馈
    - 主题切换过渡
17. **导航栏细节调优**
    - 玻璃质感效果
    - 选中指示器动画
18. **测试 & 修复**
    - 各 Tab 热切换
    - 主题切换前后一致性
    - 搜索联动
    - FAB 最小化 + 启动 i国网

**总预估: 5-7 小时**

---

## 16. 设计原则

- **形状统一**: 全局使用 8/12/16/24dp 四级圆角系统，不混用
- **色彩克制**: G 方案单一暖橙强调色，A 方案紫蓝渐变，不泛滥
- **玻璃质感**: 导航栏内发光 + 毛玻璃效果
- **间距呼吸**: `spacing * 4` 增量系统（4/8/12/16/20/24）
- **微交互**: 按压缩放、Tab 切换过渡、主题切换动画
- **一致性**: 同层级组件使用相同尺寸/圆角/间距/颜色语义
- **反向兼容**: 不改动现有业务逻辑层（service/pipeline/data），仅 UI 层变更

---

## 17. 代码仓库信息

| 项目 | 值 |
|------|-----|
| 远程仓库 | `https://github.com/LanceLee01/i_exam.git` |
| 分支 | `main` |
| 最新提交 | `12a0d60` docs: add i学助手 redesign spec document |
