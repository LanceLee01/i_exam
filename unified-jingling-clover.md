# UI 优化美化方案 — 简洁实用方向

## Context

当前 UI 存在以下问题：
- 所有颜色硬编码，无设计令牌系统
- 按钮样式单一（全 12dp 圆角 + 纯色填充）
- 状态切换无过渡动画（Loading→Done 瞬间跳变）
- 间距不一致，缺乏呼吸感
- 答案展示区可读性差，纯白文字堆砌
- 设置页面卡片千篇一律
- 无按钮按压反馈

目标：保持深色主调，提升质感、层次、动画，不改架构。

## 涉及文件

| 文件 | 改动范围 |
|------|---------|
| `theme/Theme.kt` | 重构为设计令牌系统 + 新增颜色常量 |
| `theme/Type.kt` | 微调排版比例 |
| `sidebar/SidebarPanel.kt` | 标题栏优化、底部状态栏改进 |
| `sidebar/SidebarActions.kt` | 按钮样式多样化（描边、渐变、图标强调） |
| `sidebar/SidebarStateRenderer.kt` | 状态切换 AnimatedContent、答案展示优化 |
| `sidebar/SidebarComponents.kt` | 加载动画改进 |
| `screen/SettingsScreen.kt` | 卡片层次优化 |
| `screen/KnowledgeBaseScreen.kt` | 卡片层次优化 |
| `screen/WelcomeScreen.kt` | 微交互 |

## 改动详情

### 1. 设计令牌系统 (Theme.kt)

将散落的硬编码颜色收拢为命名令牌：

```
// 主色
Primary = Blue600
PrimaryVariant = Blue800
// 表面
Surface = #121220 (侧边栏背景)
SurfaceCard = White 6% alpha (卡片)
SurfaceCardHover = White 10% alpha
// 语义色
Success = #22C55E (正确/题库匹配)
Warning = #F59E0B (保存)
Danger = #EF4444 (错误)
Info = #3B82F6 (AI模型)
// 文本
TextPrimary = White
TextSecondary = #9CA3AF
TextMuted = White 40% alpha
// 边框
BorderSubtle = White 8% alpha
BorderInput = White 15% alpha
```

在 `Theme.kt` 中新增 `ExamHelperColors` object，Compose 通过 `LocalColors` CompositionLocal 访问。

### 2. 状态过渡动画 (SidebarStateRenderer.kt)

每个状态切换用 `AnimatedContent` + `fadeIn/fadeOut`：

```kotlin
AnimatedContent(
    targetState = state,
    transitionSpec = { fadeIn(300ms) + slideInVertically(20px) togetherWith fadeOut(200ms) }
)
```

- Loading → Done: 淡入淡出替代闪变
- Preview → Loading: 平滑过渡
- 消除 `when(state)` 的硬切换

### 3. 按钮样式 (SidebarActions.kt)

| 按钮 | 当前 | 优化后 |
|------|------|--------|
| 读取屏幕 | 纯色蓝填充 | 蓝色半透明底 + 蓝色文字，更轻量 |
| 解答 | 纯色绿填充 | 绿色渐变 + 图标微动效 |
| 自动填入 | 纯色绿填充 | 绿色描边 + 填充，强调行动 |
| 重新解答 | 蓝 30% 透明 | 文字按钮，无背景 |
| 保存到题库 | 琥珀填充 | 琥珀描边 |

按钮高度统一：主要操作为 48dp，次要为 40dp。

### 4. 答案展示区 (SidebarStateRenderer.kt)

当前问题：纯白文字堆砌，难以区分题号、答案、来源。

优化：
- 每行答案前加彩色圆点指示来源：
  - 🟢 题库匹配
  - 🔵 AI模型
- 题号用 `labelSmall` + `TextSecondary` 颜色，做弱化处理
- 答案字母用等宽粗体 `titleMedium` 突出
- 行间距从 2dp → 6dp，增加呼吸感
- 来源统计显示为紧凑的 chips

### 5. 加载状态 (SidebarStateRenderer.kt)

Loading 状态增加：
- 进度百分比（从 prompt tokens / max tokens 估算）
- 已用时间（已有）+ 预估剩余时间
- Shimmer 骨架屏替代纯文字

### 6. 侧边栏标题栏 (SidebarPanel.kt)

- 标题"i考助手"左边加一个小型品牌标记（圆角方块 + 闪电图标）
- 关闭按钮改为半透明圆形底
- 整体高度从当前计算 → 固定 56dp

### 7. 卡片层次 (SettingsScreen / KnowledgeBaseScreen)

- 设置卡片增加左边缘色条（4dp 宽，primary 色）
- 卡片之间间距 6dp → 10dp
- 输入框的聚焦态增加柔和的光晕效果（`Modifier.shadow`）

### 8. 底部状态栏 (SidebarPanel.kt)

- 背景加半透明底，与主内容区分
- 状态指示器从 `●` 改为动画脉冲点
- 添加小图标（根据状态变化）

### 9. 欢迎屏幕微交互 (WelcomeScreen.kt)

- 分页点加平滑过渡动画（animateDpAsState）
- 按钮 hover/press 缩放反馈
- 图标圆形背景加旋转脉冲

## 实施顺序

1. Theme.kt 令牌化（基础，所有后续依赖）
2. SidebarActions.kt 按钮样式
3. SidebarStateRenderer.kt 动画 + 答案展示
4. SidebarPanel.kt 标题栏 + 底部栏
5. SettingsScreen.kt + KnowledgeBaseScreen.kt 卡片层次
6. WelcomeScreen.kt 微交互

## 验证

- `./gradlew assembleDebug` 编译通过
- 安装到手机，检查：侧边栏展开/关闭动画、解答流程的状态过渡、按钮按压效果、答案可读性、设置页面层次感
