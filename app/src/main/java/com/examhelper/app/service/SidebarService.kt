package com.examhelper.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.examhelper.app.ExamApplication
import com.examhelper.app.MainActivity
import com.examhelper.app.R
import com.examhelper.app.ui.sidebar.EdgeHandle
import com.examhelper.app.ui.sidebar.SidebarPanel
import com.examhelper.app.ui.theme.ExamHelperTheme

class SidebarService : Service() {

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var edgeBarView: View? = null
    private val windowOwner = WindowLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        windowOwner.attach()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowOwner.start()

        if (intent?.action == ACTION_HIDE) {
            hidePanel()
        } else {
            showEdgeBar()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        windowOwner.destroy()
        removeAllViews()
        super.onDestroy()
    }

    private fun createNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ExamApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_sidebar_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun View.bindOwner() {
        setViewTreeLifecycleOwner(windowOwner)
        setViewTreeSavedStateRegistryOwner(windowOwner)
    }

    private val edgeTouchSlop = 40
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isSwiping = false

    private fun showEdgeBar() {
        if (edgeBarView != null) return

        val edgeWidthPx = EDGE_WIDTH_DP * resources.displayMetrics.density
        val contentWidthPx = 8 * resources.displayMetrics.density

        // 外层容器：设置 lifecycle owner，让子 ComposeView 能找到
        val container = FrameLayout(this).apply {
            bindOwner()
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        isSwiping = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isSwiping) {
                            val dx = event.x - touchDownX
                            if (dx < -edgeTouchSlop) {
                                isSwiping = false
                                expandPanel()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isSwiping) {
                            isSwiping = false
                            val dx = event.x - touchDownX
                            if (kotlin.math.abs(dx) < edgeTouchSlop) {
                                expandPanel()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        // ComposeView 作为子视图，只负责显示指示条 UI
        val composeView = ComposeView(this).apply {
            setContent {
                ExamHelperTheme {
                    EdgeHandle(hasContent = true)
                }
            }
        }
        container.addView(composeView, FrameLayout.LayoutParams(
            contentWidthPx.toInt(),
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.END
        ))

        val params = WindowManager.LayoutParams(
            edgeWidthPx.toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        edgeBarView = container
        windowManager.addView(container, params)
    }

    fun expandPanel() {
        showPanel()
    }

    fun hidePanel() {
        panelView?.let {
            windowManager.removeView(it)
            panelView = null
        }
        if (edgeBarView == null) showEdgeBar()
    }

    private var panelTouchStartX = 0f
    private var panelTranslationX = 0f

    private fun showPanel() {
        edgeBarView?.let {
            windowManager.removeView(it)
            edgeBarView = null
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val panelWidth = (screenWidth * PANEL_WIDTH_RATIO).toInt()
        val panelHeight = (screenHeight * PANEL_HEIGHT_RATIO).toInt()

        val touchInterceptor = FrameLayout(this).apply {
            bindOwner()
        }

        val composeView = ComposeView(this).apply {
            setContent {
                ExamHelperTheme {
                    SidebarPanel(onHide = { hidePanel() })
                }
            }
        }

        touchInterceptor.addView(composeView)

        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        touchInterceptor.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    panelTouchStartX = event.rawX
                    panelTranslationX = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - panelTouchStartX
                    if (dx > 0) {
                        view.translationX = dx
                        panelTranslationX = dx
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (panelTranslationX > panelWidth * 0.25f) {
                        hidePanel()
                    } else {
                        view.animate().translationX(0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }

        panelView = touchInterceptor
        windowManager.addView(touchInterceptor, params)
    }

    private fun removeAllViews() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            panelView = null
        }
        edgeBarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            edgeBarView = null
        }
    }

    companion object {
        const val ACTION_HIDE = "com.examhelper.app.action.HIDE_SIDEBAR"
        const val NOTIFICATION_ID = 1001
        const val EDGE_WIDTH_DP = 24
        const val PANEL_WIDTH_RATIO = 0.65f
        const val PANEL_HEIGHT_RATIO = 0.80f
    }
}
