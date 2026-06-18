package com.examhelper.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Minimal "i" logo vector graphic. Content centered in 108x108 viewport. */
@Composable
fun AppLogo(
    size: Dp = 44.dp,
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bgStart = if (isDarkMode) Color(0xFF7C3AED) else Color(0xFFF27A3E)
    val bgEnd = if (isDarkMode) Color(0xFF2563EB) else Color(0xFFD6652D)
    val fgColor = Color.White

    // Original spec coords (from 108x108 design) centered:
    // content center x=26→54, y=(11+52)/2=31.5→54, so dx=28, dy=22.5
    val cx = 54f  // viewport center x
    val cy = 54f  // viewport center y

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.295f).dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(bgStart, bgEnd),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            ),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val s = size.toPx()
            val scale = s / 108f

            // Content in original spec coords: x∈[14,38], y∈[11,52]
            // Content center: x=26, y=31.5
            // Scale by k, then shift to viewport center (54,54)
            val k = 1.3f
            val dx = cx - 26f * k  // = 54 - 33.8 = 20.2
            val dy = cy - 31.5f * k // = 54 - 40.95 = 13.05

            // Vertical bar
            drawLine(
                color = fgColor,
                start = Offset((26f * k + dx) * scale, (20f * k + dy) * scale),
                end = Offset((26f * k + dx) * scale, (40f * k + dy) * scale),
                strokeWidth = 5f * k * scale,
                cap = StrokeCap.Round,
            )

            // Dot
            drawCircle(
                color = fgColor,
                radius = 5f * k * scale,
                center = Offset((26f * k + dx) * scale, (11f * k + dy) * scale),
            )

            // Platform line
            drawLine(
                color = fgColor.copy(alpha = 0.5f),
                start = Offset((14f * k + dx) * scale, (46f * k + dy) * scale),
                end = Offset((38f * k + dx) * scale, (46f * k + dy) * scale),
                strokeWidth = 3f * k * scale,
                cap = StrokeCap.Round,
            )

            // Diagonal accent
            drawLine(
                color = fgColor.copy(alpha = 0.35f),
                start = Offset((18f * k + dx) * scale, (38f * k + dy) * scale),
                end = Offset((34f * k + dx) * scale, (26f * k + dy) * scale),
                strokeWidth = 2.5f * k * scale,
                cap = StrokeCap.Round,
            )
        }
    }
}
