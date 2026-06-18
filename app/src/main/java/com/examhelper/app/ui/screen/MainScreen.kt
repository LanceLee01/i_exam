package com.examhelper.app.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.examhelper.app.ExamApplication
import com.examhelper.app.ui.theme.GlassBottomNavBar
import com.examhelper.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appConfig = ExamApplication.instance.appConfig

    var selectedTab by remember { mutableIntStateOf(0) }
    var isDarkMode by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        isDarkMode = appConfig.isDarkMode().first()
    }

    val colors = LocalAppColors.current

    Column(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        // Tab content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            ) { tab ->
                when (tab) {
                    0 -> HomeTab(
                        isDarkMode = isDarkMode,
                        onToggleTheme = {
                            isDarkMode = !isDarkMode
                            scope.launch { appConfig.setIsDarkMode(isDarkMode) }
                        },
                        onSearch = { query ->
                            globalSearchQuery = query
                            selectedTab = 1
                        },
                        onNavigateToTab = { selectedTab = it },
                    )
                    1 -> QuestionBankTab(
                        initialSearchQuery = globalSearchQuery,
                        isDarkMode = isDarkMode,
                        onNavigateToKB = { selectedTab = 2 },
                    )
                    2 -> KnowledgeBaseTab(isDarkMode = isDarkMode)
                    3 -> SettingsTab(isDarkMode = isDarkMode)
                }
            }
        }

        // Bottom nav bar (FAB embedded inside at same row height)
        GlassBottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onFabClick = { launchIGuoWang(context) },
            isDarkMode = isDarkMode,
        )
    }
}

private fun launchIGuoWang(context: Context) {
    Log.d("MainScreen", "launchIGuoWang called")
    val intent = context.packageManager.getLaunchIntentForPackage("com.dlxx.mam.Internal")
    if (intent != null) {
        Log.d("MainScreen", "Launching i国网: ${intent.component}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Log.d("MainScreen", "i国网 not found")
        Toast.makeText(context, "请先安装 i国网 App", Toast.LENGTH_SHORT).show()
    }
}
