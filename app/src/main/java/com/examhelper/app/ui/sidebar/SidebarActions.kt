package com.examhelper.app.ui.sidebar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState

@Composable
fun ReadScreenButton(
    isAccessibilityConnected: Boolean,
    isPending: Boolean
) {
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
}

@Composable
fun AutoFillButton(
    lastAnswer: String,
    lastExamText: String
) {
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

@Composable
fun SolveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
}

@Composable
fun ReworkButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
}

@Composable
fun SaveToKBButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
