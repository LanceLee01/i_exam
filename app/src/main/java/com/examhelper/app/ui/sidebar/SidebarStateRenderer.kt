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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ui.theme.AnswerLabel
import com.examhelper.app.ui.theme.LocalExamHelperColors
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarStateRenderer(
    state: SidebarState,
    onRework: (text: String) -> Unit,
    onSaveToKB: (text: String, answer: String) -> Unit,
    onDoneState: (answer: String, text: String, kbAnswerOptions: Map<Int, String>, resolvedQuestions: Set<Int>) -> Unit
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

                Spacer(Modifier.height(16.dp))

                // 简单进度条（缓慢增长表示处理中）
                val progress = (elapsedSec * 0.02f).coerceIn(0.05f, 0.95f)
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

                // 状态文字 + 计时
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${s.message}（${elapsedSec}s）",
                        color = colors.OnSurfaceSecondary,
                        fontSize = 14.sp
                    )
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
                Log.d("SidebarPanel", "完成状态渲染，答案长度=${s.answer.length}")
                onDoneState(s.answer, s.text, s.kbAnswerOptions, s.resolvedQuestions)

                Spacer(Modifier.height(12.dp))
                SectionHeader("答案")

                // 来源标签（仅保留题库匹配来源）
                if (s.questionSources.isNotEmpty()) {
                    val kbQuestions = s.questionSources.filterValues { it.contains("题库") }.keys.sorted()
                    val others = s.questionSources.filterValues { !it.contains("题库") }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        if (kbQuestions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.Success.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "题库 ${formatRange(kbQuestions)}",
                                    color = colors.Success,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(4.dp))
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

                // 答案逐行显示
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

                // 填入失败警告横幅
                if (s.toggleFailedQuestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xB3FF5252).copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "以下题目自动填入失败，请手动检查：${s.toggleFailedQuestions.sorted().joinToString(", ")}",
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                ReworkButton(onClick = { onRework(s.text) })
                Spacer(Modifier.height(8.dp))
                SaveToKBButton(onClick = { onSaveToKB(s.text, s.answer) })
            }

            is SidebarState.MultiRound -> Column {
                Spacer(Modifier.height(12.dp))
                SectionHeader("多轮答题")
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

            is SidebarState.Error -> Column {
                Spacer(Modifier.height(24.dp))
                StatusHint(s.message, isError = true)
            }
        }
    }
}

/** 格式化排序后的题号为范围：[1,2,3,5,6] -> "1-3 5-6" */
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
