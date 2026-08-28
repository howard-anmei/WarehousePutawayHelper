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
         * English
         */
        private const val FINISHED_RECEIVING_EN =
            "Finished Receving"

        /*
         * Spanish
         */
        private const val FINISHED_RECEIVING_ES =
            "Recepción finalizada"

        /*
         * Overlay 垂直偏移
         *
         * 之前测试好的位置。
         */
        private const val OVERLAY_Y_OFFSET =
            -48

        /*
         * 页面刷新后等待确认的时间。
         */
        private const val REMOVE_CONFIRM_DELAY =
            500L

        /*
         * 普通 Overlay：
         * 白色、低透明度。
         */
        private const val NORMAL_ALPHA =
            45

        /*
         * 红色测试 Overlay：
         * 用较明显的红色方便确认 Overlay
         * 真实存在并正在拦截。
         */
        private const val RED_ALPHA =
            100
    }

    /*
     * ==========================================
     * Overlay 状态
     * ==========================================
     */

    /*
     * Overlay 总开关。
     *
     * 默认 ON。
     */
    @Volatile
    private var overlayEnabled =
        true

    /*
     * 红色可视化开关。
     *
     * 默认 OFF。
     *
     * 只有 Overlay ON 时才有效。
     */
    @Volatile
    private var redVisualizationEnabled =
        false

    /*
     * 当前 Overlay View。
     */
    private var blockerView: View? =
        null

    /*
     * 当前 Overlay 对应的位置。
     */
    private var blockerBounds: Rect? =
        null

    /*
     * Overlay 是否已经创建。
     */
    private var blockerCreated =
        false

    /*
     * Deer 是否在前台。
     */
    private var targetAppActive =
        false

    /*
     * 主线程 Handler。
     */
    private val handler =
        Handler(Looper.getMainLooper())

    /*
     * 延迟删除任务。
     */
    private var pendingRemoveRunnable: Runnable? =
        null

    /*
     * ==========================================
     * Accessibility Service
     * ==========================================
     */

    override fun onServiceConnected() {

        super.onServiceConnected()

        serviceInfo =
            serviceInfo.apply {

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
         */
        OverlayController.attach(this)

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
            "Overlay enabled=$overlayEnabled"
        )
        Log.i(
            TAG,
            "Red visualization=$redVisualizationEnabled"
        )
        Log.i(
            TAG,
            "OVERLAY_Y_OFFSET=$OVERLAY_Y_OFFSET"
        )
        Log.i(TAG, "==========================================")

        /*
         * Service 启动后立即尝试扫描。
         *
         * 这样即使 Deer 已经在当前页面，
         * 也不用等待下一次 AccessibilityEvent。
         */
        handler.post {
            if (overlayEnabled) {
                scanCurrentWindow()
            }
        }
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
         * 1. Deer 离开前台
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
         * Deer 当前在前台。
         */
        targetAppActive =
            true

        /*
         * 取消之前可能存在的删除任务。
         */
        cancelPendingRemoval()

        /*
         * ==========================================
         * Overlay OFF
         * ==========================================
         *
         * 即使 AccessibilityService 继续运行，
         * 也不创建 Overlay。
         */
        if (!overlayEnabled) {

            if (blockerCreated) {

                removeBlocker(
                    "OVERLAY_DISABLED"
                )
            }

            return
        }

        /*
         * ==========================================
         * 点击事件日志
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
         * 扫描当前页面
         * ==========================================
         */

        scanCurrentWindow()
    }

    /**
     * 扫描当前 Accessibility Window。
     */
    private fun scanCurrentWindow() {

        if (!overlayEnabled) {
            return
        }

        if (!targetAppActive) {
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

            /*
             * 当前页面暂时找不到按钮。
             *
             * 不立即删除。
             */
            scheduleRemoveConfirmation(
                "FINISHED_RECEIVING_NOT_FOUND"
            )

            return
        }

        /*
         * 找到了。
         */
        cancelPendingRemoval()

        val bounds =
            Rect()

        node.getBoundsInScreen(
            bounds
        )

        Log.i(TAG, "==========================================")
        Log.i(
            TAG,
            "Finished Receving FOUND"
        )
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
        Log.i(
            TAG,
            "overlayEnabled=$overlayEnabled"
        )
        Log.i(
            TAG,
            "redVisualizationEnabled=$redVisualizationEnabled"
        )
        Log.i(TAG, "==========================================")

        blockerBounds =
            Rect(bounds)

        /*
         * Overlay 不存在 → 创建。
         */
        if (!blockerCreated) {

            createBlocker(
                bounds
            )

        } else {

            /*
             * Overlay 已存在 → 更新。
             */
            updateBlocker(
                bounds
            )
        }

        node.recycle()
    }

    /**
     * ==========================================
     * 寻找 Finished Receving
     * ==========================================
     *
     * 同时支持：
     *
     * English:
     * Finished Receving
     *
     * Spanish:
     * Recepción finalizada
     */
    private fun findFinishedReceiving(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()

        val text =
            node.text
                ?.toString()
                ?.trim()

        if (
            description == FINISHED_RECEIVING_EN ||
            description == FINISHED_RECEIVING_ES ||
            text == FINISHED_RECEIVING_EN ||
            text == FINISHED_RECEIVING_ES
        ) {

            Log.i(
                TAG,
                "Finished Receiving matched"
            )

            Log.i(
                TAG,
                "matchedText=$text"
            )

            Log.i(
                TAG,
                "matchedDescription=$description"
            )

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
     * ==========================================
     * 创建 Overlay
     * ==========================================
     */
    private fun createBlocker(
        bounds: Rect
    ) {

        /*
         * Overlay 被关闭时，
         * 不允许创建。
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
                     * 不执行 click。
                     */
                    return true
                }
            }

        /*
         * 设置当前颜色。
         */
        applyOverlayAppearance(
            view
        )

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

                /*
                 * 不获取焦点。
                 *
                 * 但是不能设置 NOT_TOUCHABLE。
                 */
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

            blockerBounds =
                Rect(bounds)

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
                "width=$width"
            )
            Log.i(
                TAG,
                "height=$height"
            )
            Log.i(
                TAG,
                "redVisualizationEnabled=$redVisualizationEnabled"
            )
            Log.i(
                TAG,
                "TYPE_ACCESSIBILITY_OVERLAY"
            )
            Log.i(
                TAG,
                "Touch events consumed=true"
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
     * ==========================================
     * Overlay 外观
     * ==========================================
     *
     * Red OFF:
     *   半透明白色
     *
     * Red ON:
     *   半透明红色
     */
    private fun applyOverlayAppearance(
        view: View
    ) {

        val drawable =
            GradientDrawable()

        drawable.shape =
            GradientDrawable.RECTANGLE

        drawable.cornerRadius =
            22f

        if (redVisualizationEnabled) {

            /*
             * 红色测试模式。
             */
            drawable.setColor(
                Color.argb(
                    RED_ALPHA,
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
             * 正常透明模式。
             */
            drawable.setColor(
                Color.argb(
                    NORMAL_ALPHA,
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

        view.background =
            drawable
    }

    /**
     * ==========================================
     * 更新 Overlay
     * ==========================================
     */
    private fun updateBlocker(
        bounds: Rect
    ) {

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
         * 先更新颜色。
         *
         * 即使位置没变化，
         * 红色开关也可以立即生效。
         */
        applyOverlayAppearance(
            view
        )

        /*
         * 没有位置变化就不更新 Layout。
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

            blockerBounds =
                Rect(bounds)

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
     * Overlay 开关
     * ==========================================
     *
     * 由 UI / OverlayController 调用。
     */
    fun setOverlayEnabled(
        enabled: Boolean
    ) {

        handler.post {

            Log.i(
                TAG,
                "setOverlayEnabled($enabled)"
            )

            overlayEnabled =
                enabled

            if (!enabled) {

                /*
                 * Overlay OFF 时，
                 * 红色必须自动 OFF。
                 */
                redVisualizationEnabled =
                    false

                /*
                 * 删除当前 Overlay。
                 */
                removeBlocker(
                    "OVERLAY_DISABLED_BY_UI"
                )

                Log.i(
                    TAG,
                    "Overlay OFF -> Red visualization OFF"
                )

            } else {

                /*
                 * Overlay ON。
                 *
                 * 立即重新扫描。
                 */
                if (targetAppActive) {

                    scanCurrentWindow()
                }
            }
        }
    }

    /**
     * ==========================================
     * 红色可视化开关
     * ==========================================
     *
     * 由 UI / OverlayController 调用。
     *
     * Overlay OFF 时强制保持 OFF。
     */
    fun setRedVisualizationEnabled(
        enabled: Boolean
    ) {

        handler.post {

            /*
             * Overlay OFF：
             * 红色功能不可用。
             */
            if (!overlayEnabled) {

                redVisualizationEnabled =
                    false

                Log.i(
                    TAG,
                    "Red visualization ignored because Overlay is OFF"
                )

                return@post
            }

            redVisualizationEnabled =
                enabled

            Log.i(
                TAG,
                "setRedVisualizationEnabled($enabled)"
            )

            /*
             * 当前 Overlay 已存在，
             * 直接更新颜色。
             */
            val view =
                blockerView

            if (view != null) {

                applyOverlayAppearance(
                    view
                )

                view.invalidate()

                Log.i(
                    TAG,
                    "Overlay appearance updated"
                )

            } else {

                /*
                 * Overlay 不存在，
                 * 重新扫描。
                 */
                if (targetAppActive) {

                    scanCurrentWindow()
                }
            }
        }
    }

    /**
     * ==========================================
     * 查询状态
     * ==========================================
     *
     * UI 如果以后需要显示当前状态，
     * 可以使用这两个方法。
     */
    fun isOverlayEnabled(): Boolean {
        return overlayEnabled
    }

    fun isRedVisualizationEnabled(): Boolean {
        return redVisualizationEnabled
    }

    /**
     * ==========================================
     * 延迟确认删除
     * ==========================================
     */
    private fun scheduleRemoveConfirmation(
        reason: String
    ) {

        /*
         * Overlay 没有创建，
         * 不需要删除。
         */
        if (!blockerCreated) {
            return
        }

        /*
         * Overlay 已经 OFF。
         */
        if (!overlayEnabled) {
            return
        }

        /*
         * 已经存在任务，
         * 不重复创建。
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
                 * Overlay 已经被 UI 关闭。
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
                 * 再次获取 root。
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

                    /*
                     * 确认按钮已经离开页面。
                     */
                    Log.i(
                        TAG,
                        "Finished Receving no longer found"
                    )

                    removeBlocker(
                        reason
                    )

                } else {

                    /*
                     * 按钮又出现了。
                     *
                     * 说明只是页面刷新。
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
     * ==========================================
     * 删除 Overlay
     * ==========================================
     */
    private fun removeBlocker(
        reason: String
    ) {

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

    /**
     * ==========================================
     * Service interrupted
     * ==========================================
     */
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

    /**
     * ==========================================
     * Service destroyed
     * ==========================================
     */
    override fun onDestroy() {

        Log.i(
            TAG,
            "WarehouseAccessibilityService destroyed"
        )

        targetAppActive =
            false

        removeBlocker(
            "SERVICE_DESTROYED"
        )

        /*
         * 从 Controller 注销。
         */
        OverlayController.detach(this)

        handler.removeCallbacksAndMessages(
            null
        )

        super.onDestroy()
    }
}