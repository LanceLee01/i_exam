package com.examhelper.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ui.theme.LocalAppColors

@Composable
fun SettingsTab(isDarkMode: Boolean) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // 应用图标区域
        Card(
            modifier = Modifier
                .size(88.dp)
                .padding(0.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "E",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "考试助手",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "纯本地离线版",
            fontSize = 14.sp,
            color = colors.onSurfaceSecondary,
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(
                    icon = Icons.Filled.Info,
                    title = "无需API配置",
                    description = "本版本为纯本地离线版，无需配置任何 API 密钥。",
                    colors = colors,
                )
                InfoRow(
                    icon = Icons.Filled.Info,
                    title = "完全离线",
                    description = "答题完全依赖本地题库匹配，无需联网即可使用。",
                    colors = colors,
                )
                InfoRow(
                    icon = Icons.Filled.Info,
                    title = "隐私安全",
                    description = "所有数据均存储在本地设备上，不会上传到任何服务器。",
                    colors = colors,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "导入题库文件（PPT/PDF/Excel/文本）后即可开始使用。",
            fontSize = 13.sp,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    description: String,
    colors: com.examhelper.app.ui.theme.AppColors,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = colors.onSurfaceSecondary,
                lineHeight = 18.sp,
            )
        }
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
            androidx.compose.material3.OutlinedTextField(
                value = value, onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = colors.onSurfaceMuted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (isNumeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
                    focusedBorderColor = colors.outlineInput, unfocusedBorderColor = colors.outline,
                    cursorColor = colors.primary,
                ),
            )
        }
    }
}
