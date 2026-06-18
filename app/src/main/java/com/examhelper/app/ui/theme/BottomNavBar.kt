package com.examhelper.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BottomNavItem(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
) {
    HOME("首页", "首页", Icons.Filled.Home),
    QUESTION_BANK("题库", "题库", Icons.AutoMirrored.Filled.MenuBook),
    FAB("Start", "打开 i国网", Icons.Filled.Home),
    KNOWLEDGE_BASE("知识库", "知识库", Icons.Filled.Storage),
    SETTINGS("设置", "设置", Icons.Filled.Settings),
}

@Composable
fun GlassBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onFabClick: () -> Unit,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val indicatorColor = if (isDarkMode) {
        Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF7C3AED), androidx.compose.ui.graphics.Color(0xFF2563EB)))
    } else {
        Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF27A3E), androidx.compose.ui.graphics.Color(0xFFD6652D)))
    }
    val fabBg = if (isDarkMode) {
        Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF7C3AED), androidx.compose.ui.graphics.Color(0xFF2563EB)))
    } else {
        Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF27A3E), androidx.compose.ui.graphics.Color(0xFFD6652D)))
    }
    val fabShadow = if (isDarkMode) androidx.compose.ui.graphics.Color(0x5A7C3AED) else androidx.compose.ui.graphics.Color(0x40F27A3E)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Nav bar background row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = colors.navBarShadow, ambientColor = colors.navBarShadow)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.navBarBg)
                .border(1.dp, colors.navBarBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTabItem(BottomNavItem.HOME, selectedTab == 0, indicatorColor, onClick = { onTabSelected(0) })
            NavTabItem(BottomNavItem.QUESTION_BANK, selectedTab == 1, indicatorColor, onClick = { onTabSelected(1) })
            Spacer(modifier = Modifier.width(52.dp))
            NavTabItem(BottomNavItem.KNOWLEDGE_BASE, selectedTab == 2, indicatorColor, onClick = { onTabSelected(2) })
            NavTabItem(BottomNavItem.SETTINGS, selectedTab == 3, indicatorColor, onClick = { onTabSelected(3) })
        }

        // Start FAB — overlaid on top, slightly elevated
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
                .size(52.dp)
                .shadow(8.dp, CircleShape, spotColor = fabShadow, ambientColor = fabShadow)
                .background(brush = fabBg, shape = CircleShape)
                .border(2.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFabClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Start",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.66.sp,
            )
        }
    }
}

@Composable
private fun NavTabItem(
    item: BottomNavItem,
    isSelected: Boolean,
    indicatorColor: Brush,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.onSurfaceSecondary,
        animationSpec = tween(200),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (isSelected) {
            Box(modifier = Modifier.width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(indicatorColor))
        } else {
            Spacer(modifier = Modifier.height(3.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Icon(imageVector = item.icon, contentDescription = item.contentDescription, tint = textColor, modifier = Modifier.size(22.dp))
        Text(text = item.label, color = textColor, fontSize = 8.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.24.sp)
    }
}
