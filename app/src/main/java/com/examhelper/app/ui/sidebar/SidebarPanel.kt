package com.examhelper.app.ui.sidebar

import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.pipeline.SolvePipeline
import com.examhelper.app.ui.theme.TextCorrect
import com.examhelper.app.ui.theme.TextError
import com.examhelper.app.ui.theme.TextSecondary
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.network.Reference
import com.examhelper.app.util.ExtractedTextBus.AnswerSource
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.launch

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
            val isPending = state is SidebarState.Loading
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
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

            // 自动填入按钮（有答案时显示在读取屏幕下方）
            if (lastAnswer.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(lastAnswer, lastExamText))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22C55E)
                    )
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("自动填入", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 根据状态显示内容
            when (val s = state) {
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
                    val ttftEstSec = if (ExtractedTextBus.lastTtftMs > 0) ExtractedTextBus.lastTtftMs / 1000f else 0f
                    val remainingEst = (s.maxTokens / speed).toInt()
                    val totalEst = (maxOf(elapsedSec.toFloat(), ttftEstSec) + remainingEst).toInt()
                    Log.d("DebugETA", "elapsedSec=$elapsedSec lastTtftMs=${ExtractedTextBus.lastTtftMs} maxTokens=${s.maxTokens} lastTokensPerSec=${ExtractedTextBus.lastTokensPerSec} totalEst=$totalEst")
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
                        Text("${s.message}（${elapsedSec}s 预估总时间${totalEst}s）$promptInfo", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                is SidebarState.Preview -> {
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { scope.launch { pipeline.solve(s.text) } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("解答", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

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
                    lastAnswer = s.answer
                    lastExamText = s.text

                    val optionMap = remember(s.text) { parseOptionMap(s.text) }

                    Spacer(Modifier.height(12.dp))
                    SectionHeader("答案")
                    Text(
                        text = "来源: ${s.source.label}",
                        color = Color(0xFF22C55E).copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    // 引用链接展示（如果有）
                    if (s.references.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("参考资料")
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
                            text = appendOptionText(line, optionMap),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = if (isAnswerLine) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp),
                            lineHeight = 22.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            ExtractedTextBus.updateSidebarState(
                                SidebarState.Preview(s.text)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("重新解答", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val kb = KnowledgeBaseManager.activeKB
                                if (kb == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(ExamApplication.instance, "请先激活知识库", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }
                                kb.entries.add(KBEntry(s.text, s.answer))
                                KnowledgeBaseManager.save()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(ExamApplication.instance, "已保存到「${kb.name}」(${kb.count}条)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("保存到题库", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = "── $title ──",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatusHint(message: String, isError: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Close else Icons.Filled.Inventory2,
            contentDescription = null,
            tint = if (isError) TextError else TextSecondary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) TextError else TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** 从题目文本中提取选项字母到选项文字的映射 */
private fun parseOptionMap(text: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val regex = Regex("""^([A-D])[.、．\s]\s*(.+)""", RegexOption.MULTILINE)
    regex.findAll(text).forEach { match ->
        val text2 = match.groupValues[2].trim().replace(Regex("\\s+"), "")
        if (text2.length in 2..30) {
            map[match.groupValues[1]] = text2
        }
    }
    return map
}

/** 在答案行中，将选项字母替换为"字母.选项文字" */
private fun appendOptionText(line: String, optionMap: Map<String, String>): String {
    if (optionMap.isEmpty()) return line
    var result = line
    for ((letter, text) in optionMap) {
        val hint = text.take(20)
        // "答案：A" → "答案：A.施工方案"
        result = result.replace(Regex("""答案[：:]\s*$letter(?![.\w])""")) {
            "答案：" + letter + "." + hint
        }
        // 行首或行尾的单独字母 "A" → "A.施工方案"
        result = result.replace(Regex("""(^|\s)$letter(\s*$)""")) {
            it.groupValues[1] + letter + "." + hint + it.groupValues[2]
        }
    }
    return result
}

@Composable
private fun StatusBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isError: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isError) TextError.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isError) TextError else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = if (isError) TextError else TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}


