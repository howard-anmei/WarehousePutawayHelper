package com.anmei.warehouseputawayrecorder.service

import java.lang.ref.WeakReference

object OverlayController {

    private var serviceRef:
            WeakReference<WarehouseAccessibilityService>? = null

    /*
     * AccessibilityService 启动后注册自己。
     */
    fun attach(
        service: WarehouseAccessibilityService
    ) {
        serviceRef =
            WeakReference(service)
    }

    /*
     * AccessibilityService 销毁时解除注册。
     */
    fun detach(
        service: WarehouseAccessibilityService
    ) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    /*
     * UI 控制 Overlay。
     */
    fun setOverlayEnabled(
        enabled: Boolean
    ) {
        serviceRef
            ?.get()
            ?.setOverlayEnabled(enabled)
    }

    /*
     * UI 控制红色可视化。
     */
    fun setRedVisualizationEnabled(
        enabled: Boolean
    ) {
        serviceRef
            ?.get()
            ?.setRedVisualizationEnabled(enabled)
    }
}