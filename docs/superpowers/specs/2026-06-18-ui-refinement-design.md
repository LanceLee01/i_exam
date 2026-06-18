# UI 优化美化 — 设计文档

> 日期：2026-06-18
> 范围：核心部分（设计令牌、按钮样式、状态动画、答案展示、加载增强）

## 背景

当前 UI 问题：
- 所有颜色硬编码，无设计令牌系统
- 按钮样式单一（全 12dp 圆角 + 纯色填充）
- 状态切换无过渡动画（Loading→Done 瞬间跳变）
- 答案展示区可读性差，纯白文字堆砌
- 无按钮按压反馈

目标：保持深色主调，提升质感、层次、动画。不改架构，只改 UI 层。

## 设计决策

| 决策 | 选择 |
|------|------|
| 实施策略 | 分两批：核心 → 外围 |
| 动画风格 | 淡入 + 微上浮（fadeIn 300ms + slideInVertically 20px） |
| 令牌命名 | M3 风格语义命名（Primary、Surface、OnSurface…） |

## 核心改动（第一批）

### 1. 设计令牌系统 — `theme/Theme.kt`

新增 `ExamHelperColors` object：

```
Primary       = #2563EB    (Blue600)
PrimaryVariant = #1E40AF   (Blue800)
Surface       = #121220    侧边栏背景
SurfaceCard   = White 6%   卡片底色
Success       = #22C55E    正确 / 题库匹配
Warning       = #F59E0B    保存操作
Error         = #EF4444    错误状态
Info          = #3B82F6    AI 模型
OnSurface     = White      主文字
OnSurfaceSecondary = #9CA3AF  次要文字
OnSurfaceMuted = White 40%    弱化文字
Outline       = White 8%   分隔线
OutlineInput  = White 15%  输入框边框
```

通过 `staticCompositionLocalOf` 注入 Compose 树。保留现有顶层 val 作为向后兼容别名：
- `TextSecondary` → `ExamHelperColors.OnSurfaceSecondary`
- `TextError` → `ExamHelperColors.Error`
- `Blue600` → `ExamHelperColors.Primary`
- `Blue800` → `ExamHelperColors.PrimaryVariant`
- `SidebarBg` → `ExamHelperColors.Surface`
- `EdgeWhite` → 保留原值 `Color(0x40FFFFFF)`（EdgeHandle 专用）

文件：`theme/Theme.kt`

### 2. 按钮样式多样化 — `sidebar/SidebarActions.kt`

| 按钮 | 背景 | 文字色 | 高度 |
|------|------|--------|------|
| 读取屏幕 | Primary 20% 透明 | Primary | 48dp |
| 解答 | Success 渐变（100%→80%） | White | 48dp |
| 自动填入 | Success 描边（无填充） | White | 48dp |
| 重新解答 | 无背景 | OnSurfaceSecondary | 40dp |
| 保存到题库 | Warning 描边（50% 透明） | White | 40dp |

所有按钮添加 `animateFloatAsState` 按压缩放反馈（`InteractionSource`）。

文件：`sidebar/SidebarActions.kt`

### 3. 状态过渡动画 — `sidebar/SidebarStateRenderer.kt`

用 `AnimatedContent` 包裹整个状态分发逻辑，替代当前 `when(state)` 硬切换：

```
transitionSpec = (fadeIn(300ms) + slideInVertically { it / 8 }) togetherWith fadeOut(200ms)
```

Loading → Done 淡入浮现，Preview → Loading 平滑过渡。

文件：`sidebar/SidebarStateRenderer.kt`

### 4. 答案展示区 — `sidebar/SidebarStateRenderer.kt`

**Done 状态：**
- 每行答案前加圆点指示来源（🟢 Success = 题库，🔵 Info = AI）
- 题号用 `labelSmall` + `OnSurfaceSecondary` 弱化
- 答案字母用 `titleMedium` + `FontWeight.Bold` 突出
- 行间距 2dp → 6dp

**Preview 状态：**
- 识别结果文字容器增加 `SurfaceCard` 背景 + 圆角 8dp
- 行高 22sp，提升可读性

文件：`sidebar/SidebarStateRenderer.kt`

### 5. 加载状态增强 — `sidebar/SidebarStateRenderer.kt` + `SidebarComponents.kt`

**Loading 状态新增：**
- 进度百分比文字（从已有 token 估算 + maxTokens 计算）
- 预估剩余时间 ETA（已有 `elapsedSec` + `lastTokensPerSec`）
- 线性进度条（`LinearProgressIndicator`）

**Streaming 状态新增：**
- 答案底部 Shimmer 骨架屏（`Brush.linearGradient` 循环动画）

文件：`sidebar/SidebarStateRenderer.kt`、`sidebar/SidebarComponents.kt`

### 6. 参考修正 — 其他文件

所有引用旧硬编码颜色的组件改用 `ExamHelperColors`：
- `SidebarPanel.kt`：标题栏颜色、底部状态栏
- `SidebarComponents.kt`：SectionHeader、StatusHint、StatusBanner

## 不在此批次的改动

- 侧边栏标题栏品牌标记（第二批）
- 卡片左边缘色条（第二批）
- 设置页输入框聚焦光晕（第二批）
- 欢迎页分页点动画 + 图标脉冲（第二批）
- 底部状态栏动画脉冲点（第二批）

## 验证

- `./gradlew :app:assembleDebug` 编译通过
- 所有现有单元测试通过
- 手动检查：侧边栏状态过渡动画、按钮按压反馈、答案可读性
