package com.examhelper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ui.theme.AppColors

// ── Section Title ─────────────────────────────────────────────────

@Composable
fun SectionTitle(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(vertical = 8.dp)) {
        Box(modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

// ── Search Bar ────────────────────────────────────────────────────

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    colors: AppColors,
    height: Int = 48,
    radius: Int = 14,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(colors.surfaceCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.onSurfaceSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(placeholder, color = colors.onSurfaceMuted, fontSize = 14.sp)
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────

@Composable
fun EmptyState(message: String, textColor: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
        Text(message, color = textColor, fontSize = 14.sp)
    }
}

// ── Overview Card ─────────────────────────────────────────────────

@Composable
fun OverviewCard(
    count: Int,
    label: String,
    subtitle: String? = null,
    colors: AppColors,
    accentColor: androidx.compose.ui.graphics.Color = colors.primary,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("$count", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text(label, fontSize = 13.sp, color = colors.onSurfaceSecondary)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceSecondary, modifier = Modifier.weight(1f))
                    if (trailing != null) trailing()
                }
            }
        }
    }
}
