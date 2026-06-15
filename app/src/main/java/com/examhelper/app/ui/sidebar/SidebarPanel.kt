package com.examhelper.app.ui.sidebar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.pipeline.SolvePipeline
import com.examhelper.app.ui.theme.TextSecondary
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SidebarPanel(onHide: () -> Unit) {
    val state by ExtractedTextBus.sidebarState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val pipeline = remember { SolvePipeline(ExamApplication.instance) }

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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "考试助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // ── 主内容区 ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 无障碍状态提示
            if (!isAccessibilityConnected) {
                StatusBanner(
                    icon = Icons.Filled.AccessibilityNew,
                    text = "无障碍服务未连接",
                    isError = true
                )
                Spacer(Modifier.height(8.dp))
            }

            // 读取屏幕按钮
            ReadScreenButton(
                isAccessibilityConnected = isAccessibilityConnected,
                isPending = state is SidebarState.Loading
            )

            // 自动填入按钮（有答案时显示在读取屏幕下方）
            if (lastAnswer.isNotEmpty()) {
                AutoFillButton(
                    lastAnswer = lastAnswer,
                    lastExamText = lastExamText
                )
            }

            // 根据状态显示内容
            SidebarStateRenderer(
                state = state,
                onSolve = { text -> scope.launch { pipeline.solve(text) } },
                onRework = { text ->
                    ExtractedTextBus.updateSidebarState(
                        SidebarState.Preview(text)
                    )
                },
                onSaveToKB = { text, answer ->
                    scope.launch(Dispatchers.IO) {
                        val kb = KnowledgeBaseManager.activeKB
                        if (kb == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(ExamApplication.instance, "请先激活知识库", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        kb.entries.add(KBEntry(text, answer))
                        KnowledgeBaseManager.save()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ExamApplication.instance, "已保存到「${kb.name}」(${kb.count}条)", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDoneState = { answer, text ->
                    lastAnswer = answer
                    lastExamText = text
                }
            )
        }

        // ── 底部状态栏 ──
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
