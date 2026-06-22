package com.examhelper.app.ui.sidebar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ui.theme.LocalExamHelperColors
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState

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
            containerColor = colors.Primary.copy(alpha = 0.35f),
            contentColor = Color.White,
            disabledContainerColor = colors.Primary.copy(alpha = 0.15f),
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
        border = BorderStroke(1.5.dp, colors.Success),
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

// ── TEST: 独立的翻页测试按钮 ──

@Composable
fun PageNavTestButton(label: String, onClick: () -> Unit) {
    val colors = LocalExamHelperColors.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2196F3),
            contentColor = Color.White
        )
    ) {
        Text("🔬 $label", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 多轮答题按钮 ──────────────────────────────────────────────

@Composable
fun MultiRoundButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = { if (isRunning) onStop() else onClick() },
        modifier = Modifier.fillMaxWidth().height(48.dp).scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) colors.Error.copy(alpha = 0.3f)
                            else Color(0xFF7C3AED),
            contentColor = if (isRunning) colors.Error else Color.White
        ),
        interactionSource = interactionSource
    ) {
        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.Error,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("停止多轮", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("多轮自动答题", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
