package com.examhelper.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.data.AppConfig
import com.examhelper.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(isDarkMode: Boolean) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val appConfig = ExamApplication.instance.appConfig

    var endpoint by remember { mutableStateOf(AppConfig.DEFAULT_ENDPOINT) }
    var apiKey by remember { mutableStateOf("") }
    var tavilyApiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf(AppConfig.DEFAULT_MODEL) }
    var temperature by remember { mutableFloatStateOf(AppConfig.DEFAULT_TEMPERATURE) }
    var maxTokens by remember { mutableIntStateOf(AppConfig.DEFAULT_MAX_TOKENS) }
    var systemPrompt by remember { mutableStateOf("") }
    var promptExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val snapshot = appConfig.getSnapshot()
        endpoint = snapshot.apiEndpoint
        apiKey = snapshot.apiKey
        tavilyApiKey = snapshot.tavilyApiKey
        modelName = snapshot.modelName
        temperature = snapshot.temperature
        maxTokens = snapshot.maxTokens
        systemPrompt = snapshot.systemPrompt
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ── LLM 配置 ──
        SettingsGroupHeader("LLM 配置", colors.primary, colors.onSurface)

        SettingsCard(
            icon = Icons.Filled.Link, title = "API 端点",
            value = endpoint, placeholder = "https://api.deepseek.com",
            onValueChange = { endpoint = it }, colors = colors,
        )
        SettingsCard(
            icon = Icons.Filled.Key, title = "API Key",
            value = apiKey, placeholder = "sk-...",
            onValueChange = { apiKey = it }, isPassword = true, colors = colors,
        )
        SettingsCard(
            icon = Icons.Filled.SmartToy, title = "模型名称",
            value = modelName, placeholder = "deepseek-chat",
            onValueChange = { modelName = it }, colors = colors,
        )

        // Temperature slider
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Thermostat, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Temperature: ${"%.1f".format(temperature)}", color = colors.onSurface, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = temperature, onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
                )
            }
        }

        SettingsCard(
            icon = Icons.Filled.Description, title = "Max Tokens",
            value = maxTokens.toString(), placeholder = "2048",
            onValueChange = { maxTokens = it.toIntOrNull() ?: maxTokens },
            isNumeric = true, colors = colors,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 联网搜索 ──
        SettingsGroupHeader("联网搜索", colors.info, colors.onSurface)
        SettingsCard(
            icon = Icons.Filled.Search, title = "Tavily API Key（可选）",
            value = tavilyApiKey, placeholder = "tvly-...（免费 1000 次/月）",
            onValueChange = { tavilyApiKey = it }, isPassword = true, colors = colors,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 高级设置 ──
        SettingsGroupHeader("高级设置", colors.warning, colors.onSurface)

        // System prompt (collapsible)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { promptExpanded = !promptExpanded },
                ) {
                    Icon(Icons.Filled.Api, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("系统提示词", color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (promptExpanded) "收起 ▲" else "展开 ▼",
                        color = colors.primary, style = MaterialTheme.typography.labelSmall,
                    )
                }
                AnimatedVisibility(visible = promptExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = systemPrompt, onValueChange = { systemPrompt = it },
                            placeholder = { Text("输入系统提示词...", color = colors.onSurfaceMuted) },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface, lineHeight = 18.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface,
                                focusedBorderColor = colors.outlineInput,
                                unfocusedBorderColor = colors.outline,
                                cursorColor = colors.primary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "恢复默认", color = colors.primary, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { systemPrompt = AppConfig.DEFAULT_SYSTEM_PROMPT },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save button
        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    try {
                        appConfig.setApiEndpoint(endpoint); appConfig.setApiKey(apiKey)
                        appConfig.setTavilyApiKey(tavilyApiKey); appConfig.setModelName(modelName)
                        appConfig.setTemperature(temperature); appConfig.setMaxTokens(maxTokens)
                        appConfig.setSystemPrompt(systemPrompt); appConfig.setSetupComplete(true)
                        saveMessage = "配置已保存"
                    } catch (e: Exception) { saveMessage = "保存失败: ${e.message}" }
                    isSaving = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
        ) {
            Text(if (isSaving) "保存中..." else "保存配置", fontWeight = FontWeight.Bold)
        }

        if (saveMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(saveMessage, color = if (saveMessage.contains("失败")) colors.error else colors.success, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsGroupHeader(title: String, accent: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector, title: String, value: String, placeholder: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false, isNumeric: Boolean = false,
    colors: com.examhelper.app.ui.theme.AppColors,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = colors.onSurfaceSecondary, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = colors.onSurfaceMuted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
                    focusedBorderColor = colors.outlineInput, unfocusedBorderColor = colors.outline,
                    cursorColor = colors.primary,
                ),
            )
        }
    }
}
