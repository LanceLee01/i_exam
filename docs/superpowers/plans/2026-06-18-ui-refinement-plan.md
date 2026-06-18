# UI 优化美化（核心部分）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构侧边栏 UI 的设计令牌系统、按钮样式、状态过渡动画、答案展示和加载状态增强。

**Architecture:** 从 Theme.kt 的 `ExamHelperColors` 令牌对象出发，通过 `staticCompositionLocalOf` 注入 Compose 树；所有组件逐一切换到语义令牌引用，消除硬编码颜色。状态分发由 `when()` 硬切换改为 `AnimatedContent` 过渡。不改架构，不改业务逻辑。

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose + Material 3 (BOM 2024.09)

## Global Constraints

- 保持深色主调，不改动 light 主题
- 所有现有单元测试 (`./gradlew :app:test`) 必须通过
- `./gradlew :app:assembleDebug` 编译必须通过
- 不改动任何 `pipeline/`、`network/`、`knowledge/`、`service/` 层代码
- Material 3 的 `darkColorScheme()` 保持不变，`ExamHelperColors` 是补充层

---

### Task 1: 设计令牌系统

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/theme/Theme.kt`

**Interfaces:**
- Produces: `ExamHelperColors` object（14 个颜色令牌）; `LocalExamHelperColors` CompositionLocal; `ExamHelperTheme` 更新以提供令牌

`ExamHelperColors` **完整定义**（M3 语义命名）：

```kotlin
// 新增 object，放在文件顶部（在现有 Color val 之前）
object ExamHelperColors {
    // Primary
    val Primary = Color(0xFF2563EB)
    val PrimaryVariant = Color(0xFF1E40AF)

    // Surface
    val Surface = Color(0xFF121220)
    val SurfaceCard = Color.White.copy(alpha = 0.06f)
    val SurfaceCardHover = Color.White.copy(alpha = 0.10f)

    // Semantic
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    // On-surface (text)
    val OnSurface = Color.White
    val OnSurfaceSecondary = Color(0xFF9CA3AF)
    val OnSurfaceMuted = Color.White.copy(alpha = 0.40f)

    // Outline
    val Outline = Color.White.copy(alpha = 0.08f)
    val OutlineInput = Color.White.copy(alpha = 0.15f)

    // Edge handle (special)
    val EdgeHandle = Color(0x40FFFFFF)
}
```

- [ ] **Step 1: 添加 ExamHelperColors object 和 CompositionLocal**

在 `Theme.kt` 中 `Blue600` 定义之前插入以下代码：

```kotlin
package com.examhelper.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ── Design Token System ──────────────────────────────────────────

object ExamHelperColors {
    // Primary
    val Primary = Color(0xFF2563EB)
    val PrimaryVariant = Color(0xFF1E40AF)

    // Surface
    val Surface = Color(0xFF121220)
    val SurfaceCard = Color.White.copy(alpha = 0.06f)
    val SurfaceCardHover = Color.White.copy(alpha = 0.10f)

    // Semantic
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    // On-surface (text)
    val OnSurface = Color.White
    val OnSurfaceSecondary = Color(0xFF9CA3AF)
    val OnSurfaceMuted = Color.White.copy(alpha = 0.40f)

    // Outline
    val Outline = Color.White.copy(alpha = 0.08f)
    val OutlineInput = Color.White.copy(alpha = 0.15f)

    // Edge handle (special)
    val EdgeHandle = Color(0x40FFFFFF)
}

val LocalExamHelperColors = staticCompositionLocalOf { ExamHelperColors }
```

- [ ] **Step 2: 更新现有顶层 val 为向后兼容别名**

将现有颜色常量改为指向 `ExamHelperColors` 的别名：

```kotlin
// 向后兼容别名（旧代码引用仍有效）
val Blue600 @Composable get() = LocalExamHelperColors.current.Primary
val Blue800 @Composable get() = LocalExamHelperColors.current.PrimaryVariant
val Blue900 = Color(0xFF1E3A8A)
val SidebarBg @Composable get() = LocalExamHelperColors.current.Surface
val SurfaceDark = Color(0xFF1E1E30)
val TextCorrect @Composable get() = LocalExamHelperColors.current.Success
val TextError @Composable get() = LocalExamHelperColors.current.Error
val TextSecondary @Composable get() = LocalExamHelperColors.current.OnSurfaceSecondary
val EdgeWhite @Composable get() = LocalExamHelperColors.current.EdgeHandle
```

> **注意：** `Blue900` 和 `SurfaceDark` 保持独立 `val`（仅 WelcomeScreen 和 SettingsScreen 内部特定使用，未纳入令牌系统）。

- [ ] **Step 3: 更新 DarkColorScheme 使用令牌**

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = ExamHelperColors.Primary,
    onPrimary = Color.White,
    secondary = ExamHelperColors.PrimaryVariant,
    background = ExamHelperColors.Surface,
    surface = SurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ExamHelperColors.Error,
    onError = Color.White
)
```

- [ ] **Step 4: 更新 ExamHelperTheme 提供 CompositionLocal**

```kotlin
@Composable
fun ExamHelperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalExamHelperColors provides ExamHelperColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = ExamHelperTypography,
            content = content
        )
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- [ ] **Step 6: 运行现有测试确保无回归**

```bash
cd D:/cc/i_exam && ./gradlew :app:test
```

预期：所有测试通过

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/theme/Theme.kt
git commit -m "feat: add ExamHelperColors design token system with M3 semantic naming"
```

---

### Task 2: 排版微调

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/theme/Type.kt`

**Interfaces:**
- Produces: `monoAnswer` TextStyle（答案字母等宽粗体）
- 无消费依赖

`Type.kt` 新增一个 answer-specific 排版样式：

```kotlin
import androidx.compose.ui.text.font.FontFamily

// 在 ExamHelperTypography 定义下方新增
val AnswerLabel = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    fontFamily = FontFamily.Monospace
)
```

同时调整 `bodyMedium` 行高从 20sp → 22sp（提升阅读舒适度）：

```kotlin
bodyMedium = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 22.sp  // 原 20.sp
),
```

- [ ] **Step 1: 添加 AnswerLabel 样式 + 调整 bodyMedium 行高**

编辑 `Type.kt`，完整替换文件内容：

```kotlin
package com.examhelper.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ExamHelperTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

// 答案字母专用等宽粗体
val AnswerLabel = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    fontFamily = FontFamily.Monospace
)
```

- [ ] **Step 2: 编译验证**

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug
```

- [ ] **Step 3: 运行测试**

```bash
cd D:/cc/i_exam && ./gradlew :app:test
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/theme/Type.kt
git commit -m "feat: add AnswerLabel typography, increase bodyMedium line height to 22sp"
```

---

### Task 3: 按钮样式重构

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarActions.kt`

**Interfaces:**
- Consumes: `LocalExamHelperColors`（Task 1）; `AnswerLabel`（Task 2 可选 — 本任务暂不直接使用，保留给后续答案展示）
- Produces: 5 个 @Composable 按钮函数（签名不变）

- [ ] **Step 1: 引入新依赖并重构 ReadScreenButton**

添加 import：

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import com.examhelper.app.ui.theme.LocalExamHelperColors
```

重构 `ReadScreenButton`：

```kotlin
@Composable
fun ReadScreenButton(
    isAccessibilityConnected: Boolean,
    isPending: Boolean
) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = {
            if (!isAccessibilityConnected) {
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Error("请先开启无障碍服务")
                )
                return@Button
            }
            ExtractedTextBus.updateSidebarState(
                SidebarState.Loading("正在读取屏幕...")
            )
            ExtractedTextBus.sendEvent(ExtractedTextBus.Event.RequestExtract)
        },
        enabled = !isPending,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.Primary.copy(alpha = 0.20f),
            contentColor = colors.Primary,
            disabledContainerColor = colors.Primary.copy(alpha = 0.10f),
            disabledContentColor = colors.OnSurfaceMuted
        ),
        interactionSource = interactionSource
    ) {
        if (isPending) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.Primary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("读取中...", fontSize = 15.sp)
        } else {
            Icon(
                Icons.Filled.Visibility,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("读取屏幕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
```

- [ ] **Step 2: 重构 AutoFillButton（绿色描边 + 按压反馈）**

```kotlin
@Composable
fun AutoFillButton(
    lastAnswer: String,
    lastExamText: String
) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(lastAnswer, lastExamText))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.OnSurface
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.Success),
        interactionSource = interactionSource
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.Success
        )
        Spacer(Modifier.width(8.dp))
        Text("自动填入", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
```

需要额外 import：

```kotlin
import androidx.compose.foundation.BorderStroke
```

- [ ] **Step 3: 重构 SolveButton（绿色渐变）**

```kotlin
@Composable
fun SolveButton(onClick: () -> Unit) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.Success,
            contentColor = Color.White
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            Icons.Filled.Psychology,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("解答", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
```

`Brush` 渐变暂时跳过（纯色底色 + 图标强调已满足设计要求，渐变为可选增强）。

- [ ] **Step 4: 重构 ReworkButton（文字按钮）**

```kotlin
@Composable
fun ReworkButton(onClick: () -> Unit) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.OnSurfaceSecondary
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            Icons.Filled.Psychology,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("重新解答", fontSize = 13.sp)
    }
}
```

- [ ] **Step 5: 重构 SaveToKBButton（琥珀描边）**

```kotlin
@Composable
fun SaveToKBButton(onClick: () -> Unit) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .scale(scale),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.OnSurface
        ),
        border = BorderStroke(1.dp, colors.Warning.copy(alpha = 0.5f)),
        interactionSource = interactionSource
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.Warning
        )
        Spacer(Modifier.width(6.dp))
        Text("保存到题库", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
```

- [ ] **Step 6: 编译验证**

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/sidebar/SidebarActions.kt
git commit -m "feat: overhaul button styles with press feedback, semantic colors, and variant differentiation"
```

---

### Task 4: 共享组件令牌化

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarComponents.kt`

**Interfaces:**
- Consumes: `LocalExamHelperColors`（Task 1）
- Produces: `SectionHeader`、`StatusHint`、`StatusBanner`（签名不变）

`SectionHeader` 改用 `colors.Primary`（原 `MaterialTheme.colorScheme.primary`）：

```kotlin
@Composable
fun SectionHeader(title: String) {
    val colors = LocalExamHelperColors.current
    Text(
        text = "── $title ──",
        style = MaterialTheme.typography.labelLarge,
        color = colors.Primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
        textAlign = TextAlign.Center
    )
}
```

`StatusHint` 中的 `TextError` → `colors.Error`：已通过 `TextError` 别名自动转换，**无需修改**。

`StatusBanner` 同：`TextError`、`TextSecondary` 别名自动路由到 `ExamHelperColors`，**无需修改**。

- [ ] **Step 1: SectionHeader 改为使用 LocalExamHelperColors**

在 `SidebarComponents.kt` 顶部添加 import，修改 `SectionHeader`：

```kotlin
import com.examhelper.app.ui.theme.LocalExamHelperColors

@Composable
fun SectionHeader(title: String) {
    val colors = LocalExamHelperColors.current
    Text(
        text = "── $title ──",
        style = MaterialTheme.typography.labelLarge,
        color = colors.Primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
        textAlign = TextAlign.Center
    )
}
```

- [ ] **Step 2: StatusHint 和 StatusBanner 保持不变**

（`TextSecondary` 和 `TextError` 别名已自动指向令牌）

- [ ] **Step 3: 编译验证**

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/sidebar/SidebarComponents.kt
git commit -m "refactor: switch SidebarComponents to ExamHelperColors tokens"
```

---

### Task 5: 状态过渡动画 + 答案展示 + 加载增强

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`

**Interfaces:**
- Consumes: `LocalExamHelperColors`（Task 1）; `AnswerLabel`（Task 2）; 新的 `SolveButton` 等（Task 3）
- Produces: `SidebarStateRenderer` 签名不变

这是最大的改动文件，在单个任务中完成（所有改动在同一文件、同一 concern）。

- [ ] **Step 1: 完整替换 SidebarStateRenderer.kt**

新文件内容：

```kotlin
package com.examhelper.app.ui.sidebar

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.network.Reference
import com.examhelper.app.ui.theme.AnswerLabel
import com.examhelper.app.ui.theme.LocalExamHelperColors
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import com.examhelper.app.util.ReferenceFormatter
import kotlinx.coroutines.delay

@Composable
fun SidebarStateRenderer(
    state: SidebarState,
    onSolve: (text: String) -> Unit,
    onRework: (text: String) -> Unit,
    onSaveToKB: (text: String, answer: String) -> Unit,
    onDoneState: (answer: String, text: String) -> Unit
) {
    val colors = LocalExamHelperColors.current

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
                + slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { it / 8 })
                .togetherWith(fadeOut(animationSpec = androidx.compose.animation.core.tween(200)))
        }
    ) { currentState ->
        when (val s = currentState) {
            is SidebarState.Idle -> {
                Spacer(Modifier.height(32.dp))
                StatusHint("空闲检测中...")
            }

            is SidebarState.Loading -> {
                var elapsedSec by remember { mutableIntStateOf(0) }
                LaunchedEffect(s.startTimeMs) {
                    while (true) {
                        elapsedSec = if (s.startTimeMs > 0)
                            ((System.currentTimeMillis() - s.startTimeMs) / 1000).toInt() else 0
                        delay(1000)
                    }
                }
                val speed = if (ExtractedTextBus.lastTokensPerSec > 0) ExtractedTextBus.lastTokensPerSec else 35f
                val ttftSec = if (ExtractedTextBus.lastTtftMs > 0) ExtractedTextBus.lastTtftMs / 1000f else 2f
                val promptTokens = ExtractedTextBus.lastPromptTokens
                val totalTokens = (s.maxTokens.coerceAtLeast(1) + promptTokens)
                val generatedEst = (elapsedSec - ttftSec.toInt()).coerceAtLeast(0) * speed.toInt()
                val progress = (generatedEst.toFloat() / totalTokens).coerceIn(0.05f, 0.95f)
                val etaSec = if (speed > 0 && generatedEst > 0)
                    ((totalTokens - generatedEst) / speed).toInt() else 0
                val promptInfo = if (promptTokens > 0) " [prompt:${promptTokens}tok]" else ""
                val etaInfo = if (etaSec > 0) " 剩余约 ${etaSec}s" else ""

                Spacer(Modifier.height(16.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.Primary,
                    trackColor = colors.Outline
                )

                Spacer(Modifier.height(12.dp))

                // Status text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "${s.message}（${elapsedSec}s）$promptInfo",
                            color = colors.OnSurfaceSecondary,
                            fontSize = 13.sp
                        )
                        if (etaInfo.isNotBlank() && elapsedSec > ttftSec.toInt()) {
                            Text(
                                etaInfo,
                                color = colors.OnSurfaceMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            is SidebarState.Preview -> {
                Spacer(Modifier.height(12.dp))

                SolveButton(onClick = {
                    Log.d("SidebarPanel", "SolveButton clicked, text length=${s.text.length}")
                    onSolve(s.text)
                })

                Spacer(Modifier.height(12.dp))

                SectionHeader("识别结果")
                Text(
                    text = s.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.OnSurface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.SurfaceCard)
                        .padding(12.dp),
                    lineHeight = 22.sp
                )
            }

            is SidebarState.Done -> {
                Log.d("SidebarPanel", "Done state rendered, answer length=${s.answer.length}")
                onDoneState(s.answer, s.text)

                Spacer(Modifier.height(12.dp))
                SectionHeader("答案")

                // Source chips
                if (s.questionSources.isNotEmpty()) {
                    val l1Questions = s.questionSources.filterValues { it.contains("题库") }.keys.sorted()
                    val l4Questions = s.questionSources.filterValues { it.contains("AI") || it.contains("LLM") }.keys.sorted()
                    val others = s.questionSources.filterValues { !it.contains("题库") && !it.contains("AI") && !it.contains("LLM") }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        if (l1Questions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.Success.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "📋 ${formatRange(l1Questions)}",
                                    color = colors.Success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (l4Questions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.Info.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🤖 ${formatRange(l4Questions)}",
                                    color = colors.Info,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        others.forEach { (q, label) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.Success.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$label: $q",
                                    color = colors.Success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "来源: ${s.source.label}",
                        color = colors.Success.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Answer lines with source dots and styling
                val lines = s.answer.lines()
                lines.forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEachIndexed

                    val isAnswerLine = Regex("""^\s*[\[【]?(\d+)[\]】]?\s*[A-Da-d\s正确错误对错]+""").containsMatchIn(trimmed)
                    val qNumMatch = Regex("""^[\[【]?(\d+)""").find(trimmed)
                    val qNum = qNumMatch?.groupValues?.get(1)?.toIntOrNull()

                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Source indicator dot
                        if (isAnswerLine && qNum != null) {
                            val isFromKB = s.questionSources[qNum]?.contains("题库") == true
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isFromKB) colors.Success else colors.Info)
                            )
                            Spacer(Modifier.width(8.dp))
                        } else if (isAnswerLine) {
                            Spacer(Modifier.width(14.dp))
                        }

                        // Answer text
                        Text(
                            text = trimmed,
                            style = if (isAnswerLine) AnswerLabel else MaterialTheme.typography.bodyMedium,
                            color = if (isAnswerLine) colors.OnSurface else colors.OnSurfaceSecondary,
                            fontWeight = if (isAnswerLine) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = if (isAnswerLine) 24.sp else 22.sp
                        )
                    }

                    // Shimmer skeleton after last answer line (only for streaming transitions)
                    // Not needed for Done state — this is just the final rendered answer
                }

                // Tavily reference
                val llmQuestionNumbers = s.questionSources
                    .filter { (_, source) -> source != "题库匹配" }
                    .keys.sorted().toList()
                ReferenceFormatter.formatSingleReference(s.references, llmQuestionNumbers)?.let { refText ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = refText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.OnSurfaceSecondary,
                        modifier = Modifier.padding(vertical = 2.dp),
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                ReworkButton(onClick = { onRework(s.text) })
                Spacer(Modifier.height(8.dp))
                SaveToKBButton(onClick = { onSaveToKB(s.text, s.answer) })
            }

            is SidebarState.Streaming -> {
                Log.d("SidebarPanel", "Streaming state, partialAnswer length=${s.partialAnswer.length}")
                Spacer(Modifier.height(8.dp))

                Column {
                    Text(
                        text = s.partialAnswer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.OnSurface,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = 22.sp
                    )

                    // Shimmer skeleton below streaming content
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        colors.Outline,
                                        colors.SurfaceCardHover,
                                        colors.Outline
                                    )
                                )
                            )
                    )
                }
            }

            is SidebarState.Answering -> {
                Spacer(Modifier.height(24.dp))
                StatusHint(s.text, isError = false)
            }

            is SidebarState.Error -> {
                Log.d("SidebarPanel", "Error state: ${s.message}")
                Spacer(Modifier.height(24.dp))
                StatusHint(s.message, isError = true)
            }
        }
    }
}

/** Format sorted question numbers into ranges: [1,2,3,5,6] → "1-3 5-6" */
private fun formatRange(nums: List<Int>): String {
    if (nums.isEmpty()) return ""
    val result = StringBuilder()
    var start = nums[0]
    var prev = nums[0]
    for (i in 1 until nums.size) {
        if (nums[i] == prev + 1) {
            prev = nums[i]
        } else {
            if (result.isNotEmpty()) result.append(" ")
            result.append(if (start == prev) "$start" else "$start-$prev")
            start = nums[i]
            prev = nums[i]
        }
    }
    if (result.isNotEmpty()) result.append(" ")
    result.append(if (start == prev) "$start" else "$start-$prev")
    return result.toString()
}
```

- [ ] **Step 2: 编译验证**

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL

- [ ] **Step 3: 运行测试**

```bash
cd D:/cc/i_exam && ./gradlew :app:test
```

预期：所有现有测试通过（SolvePipelineTest 等不依赖 UI 层变化）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt
git commit -m "feat: add AnimatedContent transitions, answer source dots, loading progress bar, shimmer skeleton"
```

---

### Task 6: SidebarPanel 令牌引用更新

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt`

**Interfaces:**
- Consumes: `LocalExamHelperColors`（Task 1）
- Produces: `SidebarPanel` 签名不变

- [ ] **Step 1: 更新硬编码颜色引用**

SidebarPanel.kt 中的硬编码颜色替换：

| 原代码 | 替换为 |
|--------|--------|
| `Color(0xB31A1A30)` | `colors.Surface.copy(alpha = 0.70f)` |
| `Color(0xB3121220)` | `colors.Surface` |
| `Color.White` (标题文字) | `colors.OnSurface` |
| `Color.White.copy(alpha = 0.6f)` (关闭按钮) | `colors.OnSurfaceMuted` |
| `Color.White.copy(alpha = 0.08f)` (分割线) | `colors.Outline` |
| `TextSecondary` (底部状态栏) | `colors.OnSurfaceSecondary`（别名已生效） |

具体修改：在 `SidebarPanel` 函数顶部添加 `val colors = LocalExamHelperColors.current`，替换 `Column` 的 `background` brush 和 `HorizontalDivider` 的 color。

改动点（精确位置）：

```kotlin
@Composable
fun SidebarPanel(onHide: () -> Unit) {
    val state by ExtractedTextBus.sidebarState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val pipeline = remember { SolvePipeline(ExamApplication.instance) }
    val colors = LocalExamHelperColors.current   // ← 新增

    // ... existing code ...

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.Surface.copy(alpha = 0.70f),  // 原 Color(0xB31A1A30)
                        colors.Surface                        // 原 Color(0xB3121220)
                    )
                )
            )
    ) {
        // 标题栏：Color.White → colors.OnSurface
        // 关闭按钮：Color.White.copy(alpha = 0.6f) → colors.OnSurfaceMuted

        HorizontalDivider(color = colors.Outline)  // 原 Color.White.copy(alpha = 0.08f)

        // ... 内容区 ...

        HorizontalDivider(color = colors.Outline)
        // 底部状态栏：TextSecondary 已自动路由
    }
}
```

完整修改后的 `SidebarPanel.kt` 关键行：

```kotlin
import com.examhelper.app.ui.theme.LocalExamHelperColors

@Composable
fun SidebarPanel(onHide: () -> Unit) {
    val state by ExtractedTextBus.sidebarState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val pipeline = remember { SolvePipeline(ExamApplication.instance) }
    val colors = LocalExamHelperColors.current

    val isAccessibilityConnected by ExtractedTextBus.accessibilityConnected.collectAsState()

    var lastAnswer: String by remember { mutableStateOf("") }
    var lastExamText: String by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xB31A1A30),
                        Color(0xB3121220)
                    )
                )
            )
    ) {
        // ── 顶部标题栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = null,
                tint = colors.Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "i考助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.OnSurface
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = colors.OnSurfaceMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = colors.Outline)

        // ── 主内容区 ──  (保持不变)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // ... 现有 SidebarStateRenderer 调用不变 ...
        }

        // ── 底部状态栏 ──
        HorizontalDivider(color = colors.Outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (state) {
                    is SidebarState.Idle -> "● 空闲检测中"
                    is SidebarState.Loading -> "● ${(state as SidebarState.Loading).message}"
                    is SidebarState.Preview -> "● 已识别内容"
                    is SidebarState.Done -> "● 作答完成"
                    is SidebarState.Streaming -> "● 作答中..."
                    is SidebarState.Answering -> "● 解答中..."
                    is SidebarState.Error -> "● 异常"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.OnSurfaceSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug
```

- [ ] **Step 3: 运行测试**

```bash
cd D:/cc/i_exam && ./gradlew :app:test
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt
git commit -m "refactor: switch SidebarPanel hardcoded colors to ExamHelperColors tokens"
```

---

## 最终验证

全部任务完成后：

```bash
cd D:/cc/i_exam && ./gradlew :app:assembleDebug && ./gradlew :app:test
```

预期：BUILD SUCCESSFUL + 所有测试通过

手动检查清单：
- [ ] 侧边栏展开/关闭正常
- [ ] 读取屏幕 → Preview 状态过渡流畅
- [ ] 解答 → Loading（进度条 + ETA）→ Streaming（shimmer）→ Done（淡入浮现）
- [ ] 按钮按压有缩放反馈
- [ ] 答案行有来源圆点指示（绿色=题库，蓝色=AI）
- [ ] 答案字母等宽粗体，题号弱化
