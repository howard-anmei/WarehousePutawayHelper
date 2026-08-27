package com.anmei.warehouseputawayrecorder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WarehouseAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG =
            "WarehouseAccessibility"

        private const val TARGET_PACKAGE =
            "com.weilu.deer"

        /*
         * 注意拼写保持和 Deer 实际控件一致：
         *
         * Finished Receving
         */
        private const val FINISHED_RECEIVING =
            "Finished Receving"

        /*
         * Overlay Y 偏移
         */
        private const val OVERLAY_Y_OFFSET =
            -48

        /*
         * 页面确认延迟
         */
        private const val REMOVE_CONFIRM_DELAY =
            500L

        /*
         * 正常 Overlay Alpha
         */
        private const val OVERLAY_ALPHA =
            45

        /*
         * 红色可视化 Alpha
         */
        private const val RED_OVERLAY_ALPHA =
            120
    }

    /*
     * 当前 Overlay
     */
    private var blockerView: View? = null

    /*
     * 当前 Overlay 对应的屏幕坐标
     */
    private var blockerBounds: Rect? = null

    /*
     * Overlay 是否已经创建
     */
    private var blockerCreated = false

    /*
     * ==========================================
     * Overlay 总开关
     *
     * 默认 ON
     * ==========================================
     */
    private var overlayEnabled = true

    /*
     * ==========================================
     * 红色可视化
     *
     * 默认 OFF
     * ==========================================
     */
    private var redVisualizationEnabled = false

    /*
     * Deer 是否正在前台
     */
    private var targetAppActive = false

    /*
     * 主线程 Handler
     */
    private val handler =
        Handler(Looper.getMainLooper())

    /*
     * 延迟删除任务
     */
    private var pendingRemoveRunnable: Runnable? = null

    override fun onServiceConnected() {

        super.onServiceConnected()

        serviceInfo = serviceInfo.apply {

            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED

            packageNames =
                arrayOf(TARGET_PACKAGE)

            feedbackType =
                AccessibilityServiceInfo.FEEDBACK_GENERIC

            notificationTimeout =
                50

            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        /*
         * 注册到 OverlayController。
         *
         * UI 可以通过 OverlayController
         * 控制当前 AccessibilityService。
         */
        OverlayController.attach(this)

        /*
         * 默认状态：
         *
         * Overlay ON
         * 红色 OFF
         */
        overlayEnabled = true
        redVisualizationEnabled = false

        Log.i(TAG, "==========================================")
        Log.i(
            TAG,
            "WarehouseAccessibilityService connected"
        )
        Log.i(
            TAG,
            "TARGET_PACKAGE=$TARGET_PACKAGE"
        )
        Log.i(
            TAG,
            "MODE=FINAL OVERLAY BLOCK"
        )
        Log.i(
            TAG,
            "OVERLAY_Y_OFFSET=$OVERLAY_Y_OFFSET"
        )
        Log.i(
            TAG,
            "REMOVE_CONFIRM_DELAY=$REMOVE_CONFIRM_DELAY"
        )
        Log.i(
            TAG,
            "OVERLAY_ALPHA=$OVERLAY_ALPHA"
        )
        Log.i(
            TAG,
            "overlayEnabled=$overlayEnabled"
        )
        Log.i(
            TAG,
            "redVisualizationEnabled=" +
                    redVisualizationEnabled
        )
        Log.i(TAG, "==========================================")
    }

    /*
     * ==========================================
     * UI → Overlay 总开关
     * ==========================================
     */
    fun setOverlayEnabled(
        enabled: Boolean
    ) {

        overlayEnabled =
            enabled

        Log.i(
            TAG,
            "Overlay enabled = $enabled"
        )

        /*
         * Overlay OFF：
         *
         * 1. 红色自动 OFF
         * 2. 删除当前 Overlay
         */
        if (!enabled) {

            redVisualizationEnabled =
                false

            removeBlocker(
                "OVERLAY_DISABLED_BY_UI"
            )

            return
        }

        /*
         * Overlay ON：
         *
         * 如果 Deer 当前正在前台，
         * 重新扫描当前页面。
         */
        if (targetAppActive) {

            Log.i(
                TAG,
                "Overlay enabled by UI, scanning current window"
            )

            scanCurrentWindow()
        }
    }

    /*
     * ==========================================
     * UI → 红色可视化
     * ==========================================
     */
    fun setRedVisualizationEnabled(
        enabled: Boolean
    ) {

        /*
         * Overlay 没打开时，
         * 红色不能开启。
         */
        if (!overlayEnabled) {

            redVisualizationEnabled =
                false

            Log.i(
                TAG,
                "Red visualization ignored because Overlay is OFF"
            )

            return
        }

        redVisualizationEnabled =
            enabled

        Log.i(
            TAG,
            "Red visualization = $enabled"
        )

        /*
         * 立即改变现有 Overlay 的颜色。
         */
        updateBlockerAppearance()
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (event == null) {
            return
        }

        val packageName =
            event.packageName?.toString()

        /*
         * ==========================================
         * 1. Deer 离开
         * ==========================================
         */
        if (packageName != TARGET_PACKAGE) {

            if (targetAppActive) {

                Log.i(TAG, "==========================================")
                Log.i(
                    TAG,
                    "Target app left: $packageName"
                )
                Log.i(
                    TAG,
                    "Removing Finished Receving blocker"
                )
                Log.i(TAG, "==========================================")

                targetAppActive =
                    false

                cancelPendingRemoval()

                removeBlocker(
                    "TARGET_APP_LEFT:$packageName"
                )
            }

            return
        }

        /*
         * Deer 当前在前台
         */
        targetAppActive =
            true

        /*
         * 如果之前有延迟删除，
         * Deer 又回到了有效页面。
         */
        cancelPendingRemoval()

        /*
         * ==========================================
         * 点击事件
         * ==========================================
         */
        if (
            event.eventType ==
            AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {

            Log.w(
                TAG,
                "TYPE_VIEW_CLICKED received: $packageName"
            )

            Log.w(
                TAG,
                "eventText=${event.text}"
            )

            Log.w(
                TAG,
                "eventContentDescription=" +
                        event.contentDescription
            )
        }

        /*
         * ==========================================
         * 扫描当前窗口
         * ==========================================
         */
        scanCurrentWindow()
    }

    /**
     * 扫描当前 Accessibility Window。
     */
    private fun scanCurrentWindow() {

        /*
         * Overlay OFF：
         *
         * Service 继续运行，
         * 但不创建 Overlay。
         */
        if (!overlayEnabled) {
            return
        }

        val root =
            rootInActiveWindow

        if (root == null) {

            Log.w(
                TAG,
                "rootInActiveWindow == null"
            )

            scheduleRemoveConfirmation(
                "ROOT_NULL"
            )

            return
        }

        val node =
            findFinishedReceiving(root)

        if (node == null) {

            scheduleRemoveConfirmation(
                "FINISHED_RECEVING_NOT_FOUND"
            )

            return
        }

        /*
         * 找到了。
         */
        cancelPendingRemoval()

        val bounds =
            Rect()

        node.getBoundsInScreen(bounds)

        Log.i(TAG, "==========================================")
        Log.i(TAG, "Finished Receving FOUND")
        Log.i(
            TAG,
            "className=${node.className}"
        )
        Log.i(
            TAG,
            "contentDescription=${node.contentDescription}"
        )
        Log.i(
            TAG,
            "clickable=${node.isClickable}"
        )
        Log.i(
            TAG,
            "enabled=${node.isEnabled}"
        )
        Log.i(
            TAG,
            "visible=${node.isVisibleToUser}"
        )
        Log.i(
            TAG,
            "bounds=$bounds"
        )
        Log.i(TAG, "==========================================")

        blockerBounds =
            Rect(bounds)

        /*
         * Overlay 不存在 → 创建
         */
        if (!blockerCreated) {

            createBlocker(
                bounds
            )

        } else {

            /*
             * Overlay 已存在 → 更新
             */
            updateBlocker(
                bounds
            )
        }

        node.recycle()
    }

    /**
     * 递归寻找 Finished Receving。
     */
    private fun findFinishedReceiving(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val description =
            node.contentDescription
                ?.toString()

        val text =
            node.text
                ?.toString()

        if (
            description == FINISHED_RECEIVING ||
            text == FINISHED_RECEIVING
        ) {

            return AccessibilityNodeInfo.obtain(
                node
            )
        }

        for (
        i in 0 until node.childCount
        ) {

            val child =
                node.getChild(i)
                    ?: continue

            val result =
                findFinishedReceiving(
                    child
                )

            child.recycle()

            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * 创建 Overlay。
     */
    private fun createBlocker(
        bounds: Rect
    ) {

        /*
         * Overlay OFF 时绝对不能创建。
         */
        if (!overlayEnabled) {
            return
        }

        /*
         * 防止重复创建。
         */
        if (blockerView != null) {
            return
        }

        /*
         * ==========================================
         * 真正拦截触摸的 View
         * ==========================================
         */
        val view =
            object : View(this) {

                override fun onTouchEvent(
                    event: MotionEvent
                ): Boolean {

                    Log.i(
                        TAG,
                        "BLOCKED TOUCH: " +
                                "action=${event.actionMasked} " +
                                "x=${event.rawX} " +
                                "y=${event.rawY}"
                    )

                    /*
                     * 永远消费触摸事件。
                     */
                    return true
                }

                override fun performClick(): Boolean {

                    /*
                     * 不执行任何 click。
                     *
                     * 不把事件继续交给
                     * Finished Receving。
                     */
                    return true
                }
            }

        /*
         * ==========================================
         * Overlay Drawable
         * ==========================================
         */
        val drawable =
            GradientDrawable()

        drawable.shape =
            GradientDrawable.RECTANGLE

        drawable.cornerRadius =
            22f

        /*
         * 初始正常颜色。
         */
        drawable.setColor(
            Color.argb(
                OVERLAY_ALPHA,
                255,
                255,
                255
            )
        )

        drawable.setStroke(
            2,
            Color.argb(
                80,
                255,
                255,
                255
            )
        )

        view.background =
            drawable

        /*
         * ==========================================
         * 尺寸
         * ==========================================
         */
        val width =
            bounds.width()
                .coerceAtLeast(1)

        val height =
            bounds.height()
                .coerceAtLeast(1)

        /*
         * ==========================================
         * Window 参数
         * ==========================================
         */
        val params =
            WindowManager.LayoutParams(
                width,
                height,

                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.START

        params.x =
            bounds.left

        params.y =
            bounds.top +
                    OVERLAY_Y_OFFSET

        /*
         * 获取 WindowManager。
         */
        val wm =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        try {

            wm.addView(
                view,
                params
            )

            blockerView =
                view

            blockerCreated =
                true

            /*
             * 根据当前红色状态设置颜色。
             */
            updateBlockerAppearance()

            Log.i(TAG, "==========================================")
            Log.i(
                TAG,
                "Finished Receving BLOCKER CREATED"
            )
            Log.i(
                TAG,
                "originalBounds=$bounds"
            )
            Log.i(
                TAG,
                "overlayX=${params.x}"
            )
            Log.i(
                TAG,
                "overlayY=${params.y}"
            )
            Log.i(
                TAG,
                "OVERLAY_Y_OFFSET=$OVERLAY_Y_OFFSET"
            )
            Log.i(
                TAG,
                "width=$width"
            )
            Log.i(
                TAG,
                "height=$height"
            )
            Log.i(
                TAG,
                "TYPE_ACCESSIBILITY_OVERLAY"
            )
            Log.i(
                TAG,
                "Touch events consumed=true"
            )
            Log.i(
                TAG,
                "redVisualizationEnabled=" +
                        redVisualizationEnabled
            )
            Log.i(TAG, "==========================================")

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create blocker",
                e
            )
        }
    }

    /**
     * 更新 Overlay 位置和尺寸。
     */
    private fun updateBlocker(
        bounds: Rect
    ) {

        if (!overlayEnabled) {
            return
        }

        val view =
            blockerView
                ?: return

        val wm =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        val params =
            view.layoutParams
                    as? WindowManager.LayoutParams
                ?: return

        val newWidth =
            bounds.width()
                .coerceAtLeast(1)

        val newHeight =
            bounds.height()
                .coerceAtLeast(1)

        val newX =
            bounds.left

        val newY =
            bounds.top +
                    OVERLAY_Y_OFFSET

        /*
         * 没有变化就不更新。
         */
        if (
            params.width == newWidth &&
            params.height == newHeight &&
            params.x == newX &&
            params.y == newY
        ) {

            return
        }

        params.width =
            newWidth

        params.height =
            newHeight

        params.x =
            newX

        params.y =
            newY

        try {

            wm.updateViewLayout(
                view,
                params
            )

            Log.i(
                TAG,
                "Finished Receving BLOCKER UPDATED"
            )

            Log.i(
                TAG,
                "originalBounds=$bounds"
            )

            Log.i(
                TAG,
                "overlayX=${params.x}"
            )

            Log.i(
                TAG,
                "overlayY=${params.y}"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to update blocker",
                e
            )
        }
    }

    /**
     * ==========================================
     * 更新 Overlay 外观
     * ==========================================
     */
    private fun updateBlockerAppearance() {

        val view =
            blockerView
                ?: return

        val drawable =
            view.background as? GradientDrawable
                ?: return

        if (redVisualizationEnabled) {

            /*
             * 红色可视化：
             *
             * 半透明红色
             * +
             * 红色边框
             */
            drawable.setColor(
                Color.argb(
                    RED_OVERLAY_ALPHA,
                    255,
                    0,
                    0
                )
            )

            drawable.setStroke(
                3,
                Color.RED
            )

        } else {

            /*
             * 正常 Overlay：
             *
             * 半透明白色
             * +
             * 白色边框
             */
            drawable.setColor(
                Color.argb(
                    OVERLAY_ALPHA,
                    255,
                    255,
                    255
                )
            )

            drawable.setStroke(
                2,
                Color.argb(
                    80,
                    255,
                    255,
                    255
                )
            )
        }

        /*
         * 立即刷新。
         */
        view.invalidate()

        Log.i(
            TAG,
            "Overlay appearance updated: " +
                    "red=$redVisualizationEnabled"
        )
    }

    /**
     * 延迟确认删除。
     */
    private fun scheduleRemoveConfirmation(
        reason: String
    ) {

        /*
         * Overlay OFF 时不处理。
         */
        if (!overlayEnabled) {
            return
        }

        /*
         * 已经没有 Overlay。
         */
        if (!blockerCreated) {
            return
        }

        /*
         * 已经有任务。
         */
        if (pendingRemoveRunnable != null) {
            return
        }

        Log.i(
            TAG,
            "Scheduling blocker removal confirmation: $reason"
        )

        val runnable =
            Runnable {

                pendingRemoveRunnable =
                    null

                /*
                 * Overlay 在确认期间被关闭。
                 */
                if (!overlayEnabled) {

                    removeBlocker(
                        "OVERLAY_DISABLED_DURING_CONFIRMATION"
                    )

                    return@Runnable
                }

                /*
                 * Deer 已经离开。
                 */
                if (!targetAppActive) {

                    removeBlocker(
                        "TARGET_APP_INACTIVE"
                    )

                    return@Runnable
                }

                /*
                 * Deer 仍然在前台。
                 */
                val root =
                    rootInActiveWindow

                if (root == null) {

                    removeBlocker(
                        "ROOT_NULL_CONFIRMED"
                    )

                    return@Runnable
                }

                val node =
                    findFinishedReceiving(
                        root
                    )

                if (node == null) {

                    Log.i(
                        TAG,
                        "Finished Receving no longer found"
                    )

                    removeBlocker(
                        reason
                    )

                } else {

                    /*
                     * 又找到了。
                     *
                     * 说明刚才只是页面刷新。
                     */
                    val bounds =
                        Rect()

                    node.getBoundsInScreen(
                        bounds
                    )

                    Log.i(
                        TAG,
                        "Finished Receving returned during removal check"
                    )

                    blockerBounds =
                        Rect(bounds)

                    if (!blockerCreated) {

                        createBlocker(
                            bounds
                        )

                    } else {

                        updateBlocker(
                            bounds
                        )
                    }

                    node.recycle()
                }
            }

        pendingRemoveRunnable =
            runnable

        handler.postDelayed(
            runnable,
            REMOVE_CONFIRM_DELAY
        )
    }

    /**
     * 取消延迟删除。
     */
    private fun cancelPendingRemoval() {

        val runnable =
            pendingRemoveRunnable
                ?: return

        handler.removeCallbacks(
            runnable
        )

        pendingRemoveRunnable =
            null
    }

    /**
     * 删除 Overlay。
     */
    private fun removeBlocker(
        reason: String
    ) {

        /*
         * 先取消延迟任务。
         */
        cancelPendingRemoval()

        val view =
            blockerView

        if (view == null) {

            blockerCreated =
                false

            blockerBounds =
                null

            return
        }

        val wm =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        try {

            wm.removeView(
                view
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "removeView failed",
                e
            )
        }

        blockerView =
            null

        blockerCreated =
            false

        blockerBounds =
            null

        Log.i(TAG, "==========================================")
        Log.i(
            TAG,
            "Finished Receving BLOCKER REMOVED"
        )
        Log.i(
            TAG,
            "reason=$reason"
        )
        Log.i(TAG, "==========================================")
    }

    override fun onInterrupt() {

        Log.w(
            TAG,
            "WarehouseAccessibilityService interrupted"
        )

        targetAppActive =
            false

        removeBlocker(
            "SERVICE_INTERRUPTED"
        )
    }

    override fun onDestroy() {

        Log.i(
            TAG,
            "WarehouseAccessibilityService destroyed"
        )

        /*
         * 从 Controller 中解除注册。
         */
        OverlayController.detach(this)

        targetAppActive =
            false

        removeBlocker(
            "SERVICE_DESTROYED"
        )

        handler.removeCallbacksAndMessages(
            null
        )

        super.onDestroy()
    }
}