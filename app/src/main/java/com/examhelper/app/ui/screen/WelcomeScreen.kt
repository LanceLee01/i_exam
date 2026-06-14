package com.examhelper.app.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WelcomeStep(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val steps = listOf(
    WelcomeStep(
        icon = Icons.Filled.Psychology,
        title = "考试助手",
        description = "在 i国网 考试时，智能识别屏幕文字，调用大模型实时解答"
    ),
    WelcomeStep(
        icon = Icons.Filled.Layers,
        title = "悬浮窗权限",
        description = "需要在其他应用上方显示答案侧边栏"
    ),
    WelcomeStep(
        icon = Icons.Filled.AccessibilityNew,
        title = "无障碍服务",
        description = "需要读取考试界面的文字内容"
    ),
    WelcomeStep(
        icon = Icons.Filled.Api,
        title = "配置 API",
        description = "配置 OpenAI 协议兼容的 API 端点与密钥"
    )
)

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF121220)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(40.dp))

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            }
        ) { stepIndex ->
            val step = steps[stepIndex]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        // Step indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            steps.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (index == currentStep) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentStep) Color.White
                            else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (currentStep < steps.size - 1) {
                    currentStep++
                } else {
                    onGetStarted()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (currentStep < steps.size - 1) "下一步" else "开始使用",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
