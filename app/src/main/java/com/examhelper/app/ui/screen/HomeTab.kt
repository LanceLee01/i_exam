package com.examhelper.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ui.theme.AppLogo
import com.examhelper.app.ui.theme.LocalAppColors

@Composable
fun HomeTab(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onSearch: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // ── Brand Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(size = 44.dp, isDarkMode = isDarkMode)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "i学助手",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    "学海无涯，有我做舟",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.onSurfaceSecondary,
                )
            }

            // Theme toggle
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceAccent),
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDarkMode) "切换到亮色" else "切换到暗色",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Search Bar ──
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceCard),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = colors.onSurface,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索题库...",
                                    color = colors.onSurfaceMuted,
                                    fontSize = 14.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary)
                            .clickable { onSearch(searchQuery) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("搜索", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Feature Overview ──
        SectionTitle("功能概览", colors.onSurface, colors.primary)

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            icon = Icons.Filled.Visibility,
            title = "屏幕读取",
            description = "通过无障碍服务提取考试题目",
            iconColor = colors.primary,
            surfaceCard = colors.surfaceCard,
            onSurfaceSecondary = colors.onSurfaceSecondary,
            onSurface = colors.onSurface,
        )

        FeatureCard(
            icon = Icons.Filled.AutoAwesome,
            title = "智能答题",
            description = "题库匹配 + 知识库检索 + AI 直接解答",
            iconColor = colors.success,
            surfaceCard = colors.surfaceCard,
            onSurfaceSecondary = colors.onSurfaceSecondary,
            onSurface = colors.onSurface,
        )

        FeatureCard(
            icon = Icons.AutoMirrored.Filled.List,
            title = "自动填入",
            description = "解析答案后自动点击考试界面选项",
            iconColor = colors.warning,
            surfaceCard = colors.surfaceCard,
            onSurfaceSecondary = colors.onSurfaceSecondary,
            onSurface = colors.onSurface,
        )

        FeatureCard(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "知识库管理",
            description = "Excel 题库导入 + 文档解析入库",
            iconColor = colors.info,
            surfaceCard = colors.surfaceCard,
            onSurfaceSecondary = colors.onSurfaceSecondary,
            onClick = { onNavigateToTab(2) },
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(title: String, textColor: androidx.compose.ui.graphics.Color, accent: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    iconColor: androidx.compose.ui.graphics.Color,
    surfaceCard: androidx.compose.ui.graphics.Color,
    onSurfaceSecondary: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
            Text(description, fontSize = 12.sp, color = onSurfaceSecondary)
        }
    }
}
