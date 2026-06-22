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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarStateRenderer(
    state: SidebarState,
    onRework: (text: String) -> Unit,
    onSaveToKB: (text: String, answer: String) -> Unit,
    onDoneState: (answer: String, text: String, kbAnswerOptions: Map<Int, String>) -> Unit
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
            is SidebarState.Idle -> Column {
                Spacer(Modifier.height(32.dp))
                StatusHint("空闲检测中...")
            }

            is SidebarState.Loading -> Column {
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
                            fontSize = 14.sp
                        )
                        if (etaInfo.isNotBlank() && elapsedSec > ttftSec.toInt()) {
                            Text(
                                etaInfo,
                                color = colors.OnSurfaceMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            is SidebarState.Preview -> Column {
                Spacer(Modifier.height(12.dp))

                SectionHeader("识别结果")
                Text(
                    text = s.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.OnSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.SurfaceCardHover)
                        .padding(12.dp),
                    lineHeight = 24.sp
                )
            }

            is SidebarState.Done -> Column {
                Log.d("SidebarPanel", "Done state rendered, answer length=${s.answer.length}")
                onDoneState(s.answer, s.text, s.kbAnswerOptions)

                Spacer(Modifier.height(12.dp))
                SectionHeader("答案")

                // Source chips
                if (s.questionSources.isNotEmpty()) {
                    val l1Questions = s.questionSources.filterValues { it.contains("题库") }.keys.sorted()
                    val l4Questions = s.questionSources.filterValues { it.contains("AI") || it.contains("LLM") }.keys.sorted()
                    val others = s.questionSources.filterValues { !it.contains("题库") && !it.contains("AI") && !it.contains("LLM") }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        if (l1Questions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.Success.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "📋 ${formatRange(l1Questions)}",
                                    color = colors.Success,
                                    fontSize = 12.sp,
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
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🤖 ${formatRange(l4Questions)}",
                                    color = colors.Info,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        others.forEach { (q, label) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.Success.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$label: $q",
                                    color = colors.Success,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "来源: ${s.source.label}",
                        color = colors.Success.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Answer lines
                val lines = s.answer.lines()
                lines.forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEachIndexed

                    val qNum = Regex("""^[\[【]?(\d+)[\]】]?[、.\s:：]""").find(trimmed)
                        ?.groupValues?.get(1)?.toIntOrNull()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (qNum != null) {
                            val isFromKB = s.questionSources[qNum]?.contains("题库") == true
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isFromKB) colors.Success else colors.Info)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text = trimmed, style = AnswerLabel, color = colors.OnSurface)
                        } else {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = trimmed,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.OnSurfaceSecondary
                            )
                        }
                    }
                }

                // Tavily reference
                val llmQuestionNumbers = s.questionSources
                    .filter { (_, source) -> source != "题库匹配" }
                    .keys.sorted().toList()
                ReferenceFormatter.formatSingleReference(s.references, llmQuestionNumbers)?.let { refText ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = refText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.OnSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp),
                        lineHeight = 22.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                ReworkButton(onClick = { onRework(s.text) })
                Spacer(Modifier.height(8.dp))
                SaveToKBButton(onClick = { onSaveToKB(s.text, s.answer) })
            }

            is SidebarState.Streaming -> Column {
                Log.d("SidebarPanel", "Streaming state, partialAnswer length=${s.partialAnswer.length}")
                Spacer(Modifier.height(8.dp))

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

            is SidebarState.Answering -> Column {
                Spacer(Modifier.height(24.dp))
                StatusHint(s.text, isError = false)
            }

            is SidebarState.Error -> Column {
                Log.d("SidebarPanel", "Error state: ${s.message}")
                Spacer(Modifier.height(24.dp))
                StatusHint(s.message, isError = true)
            }

            is SidebarState.MultiRound -> Column {
                // Placeholder — will be fully rendered in Task 5
                Spacer(Modifier.height(12.dp))
                SectionHeader("🔄 多轮答题")
                Text(
                    when (s.phase) {
                        SidebarState.MultiPhase.SCANNING -> "扫描中... 第 ${s.currentPage} 页"
                        SidebarState.MultiPhase.SOLVING -> "解答中..."
                        SidebarState.MultiPhase.FILLING -> buildString {
                            append("填入中... ${s.answeredCount}/${s.totalPages}")
                            if (s.currentQuestionSummary.isNotBlank()) {
                                append("\n")
                                append(s.currentQuestionSummary)
                            } else {
                                append("\n(摘要为空)")
                            }
                        }
                        SidebarState.MultiPhase.DONE -> "完成! 共 ${s.totalPages} 题"
                        SidebarState.MultiPhase.ERROR -> s.errorMessage.ifBlank { "未知错误" }
                    },
                    color = colors.OnSurface,
                    fontSize = 14.sp
                )
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
