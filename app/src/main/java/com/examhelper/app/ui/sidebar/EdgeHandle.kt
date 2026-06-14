package com.examhelper.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 屏幕右边缘的半透明指示条
 */
@Composable
fun EdgeHandle(
    hasContent: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.4f)
                .width(4.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(Color.White.copy(alpha = 0.5f))
        )
    }
}
