package com.examhelper.app.ui.sidebar

import android.util.Log
import androidx.compose.foundation.background

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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.network.Reference
import com.examhelper.app.ui.theme.TextSecondary
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.delay

@Composable
fun SidebarStateRenderer(
    state: SidebarState,
    onSolve: (text: String) -> Unit,
    onRework: (text: String) -> Unit,
    onSaveToKB: (text: String, answer: String) -> Unit,
    onDoneState: (answer: String, text: String) -> Unit
) {
    when (val s = state) {
        is SidebarState.Idle -> {
            Spacer(Modifier.height(32.dp))
            StatusHint("空闲检测中...")
        }

        is SidebarState.Loading -> {
            var elapsedSec by remember { mutableStateOf<Int>(0) }
            LaunchedEffect(s.startTimeMs) {
                while (true) {
                    elapsedSec = if (s.startTimeMs > 0)
                        ((System.currentTimeMillis() - s.startTimeMs) / 1000).toInt() else 0
                    delay(1000)
                }
            }
            val speed = if (ExtractedTextBus.lastTokensPerSec > 0) ExtractedTextBus.lastTokensPerSec else 35f
            val ttftEstSec = if (ExtractedTextBus.lastTtftMs > 0) ExtractedTextBus.lastTtftMs / 1000f else 0f
            val promptInfo = if (ExtractedTextBus.lastPromptTokens > 0)
                " [prompt:${ExtractedTextBus.lastPromptTokens}tok]" else ""
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("${s.message}（${elapsedSec}s）$promptInfo", color = TextSecondary, fontSize = 13.sp)
            }
        }

        is SidebarState.Preview -> {
            Spacer(Modifier.height(12.dp))

            SolveButton(onClick = { onSolve(s.text) })

            Spacer(Modifier.height(12.dp))

            SectionHeader("识别结果")
            Text(
                text = s.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp),
                lineHeight = 22.sp
            )
        }

        is SidebarState.Done -> {
            Log.d("SidebarPanel", "Done state rendered, answer length=${s.answer.length}")
            onDoneState(s.answer, s.text)

            Spacer(Modifier.height(12.dp))
            SectionHeader("答案")
            if (s.questionSources.isNotEmpty()) {
                val l1Questions = s.questionSources.filterValues { it.contains("题库") }.keys.sorted()
                val l4Questions = s.questionSources.filterValues { it.contains("AI") || it.contains("LLM") }.keys.sorted()
                val others = s.questionSources.filterValues { !it.contains("题库") && !it.contains("AI") && !it.contains("LLM") }
                Column {
                    if (l1Questions.isNotEmpty()) {
                        Text(
                            text = "📋 题库匹配: ${l1Questions.joinToString(", ")}",
                            color = Color(0xFF22C55E).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (l4Questions.isNotEmpty()) {
                        Text(
                            text = "🤖 AI模型: ${l4Questions.joinToString(", ")}",
                            color = Color(0xFF3B82F6).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    others.forEach { (q, label) ->
                        Text(
                            text = "$label: $q",
                            color = Color(0xFF22C55E).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "来源: ${s.source.label}",
                    color = Color(0xFF22C55E).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            // 引用链接展示（如果有）
            if (s.references.isNotEmpty()) {
                val llmQuestions = s.questionSources
                    .filterValues { it.contains("AI") || it.contains("LLM") }
                    .keys
                    .sorted()
                
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    if (llmQuestions.isNotEmpty()) "🔍 参考资料（题 ${llmQuestions.joinToString(", ")}）"
                    else "🔍 参考资料"
                )
                Column {
                    s.references.take(5).forEachIndexed { index, ref ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "[${index + 1}] ",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = ref.title,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = ref.url,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val lines = s.answer.lines()
            lines.forEach { line ->
                val isAnswerLine = line.contains("✓") ||
                    Regex("""^\s*[\[【]?\d+[\]】]?\s*[A-Da-d]\b""").containsMatchIn(line)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = if (isAnswerLine) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 2.dp),
                    lineHeight = 22.sp
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
            Text(
                text = s.partialAnswer,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 22.sp
            )
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


