package com.anmei.warehouseputawayrecorder.ui.scanrecord

import com.anmei.warehouseputawayrecorder.service.OverlayController
import com.anmei.warehouseputawayrecorder.ui.theme.MyApplicationTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey

@Composable
fun ScanRecordScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanRecordViewModel = hiltViewModel(),
) {
    val items by viewModel.uiState.collectAsStateWithLifecycle()

    /*
     * 当前页面已经不再显示 ScanRecord 输入框、
     * Save Button 和 saved items。
     *
     * 这里只显示 Overlay 控制 UI。
     */
    ScanRecordScreen(
        modifier = modifier
    )
}

@Composable
internal fun ScanRecordScreen(
    modifier: Modifier = Modifier,
) {

    /*
     * ==========================================
     * Overlay 状态
     *
     * 默认：
     * Overlay ON
     * 红色 OFF
     * ==========================================
     */
    var overlayEnabled by remember {
        mutableStateOf(true)
    }

    var redVisualizationEnabled by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        Text(
            text = "Warehouse Putaway Recorder"
        )

        /*
         * ==========================================
         * Overlay 开关
         * ==========================================
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    text = "Overlay"
                )

                Text(
                    text = "控制 Overlay 是否显示"
                )
            }

            Switch(
                checked = overlayEnabled,

                onCheckedChange = { enabled ->

                    /*
                     * 更新 UI 状态
                     */
                    overlayEnabled =
                        enabled

                    /*
                     * Overlay OFF：
                     *
                     * 红色自动 OFF
                     */
                    if (!enabled) {

                        redVisualizationEnabled =
                            false
                    }

                    /*
                     * 通知 AccessibilityService
                     */
                    OverlayController
                        .setOverlayEnabled(
                            enabled
                        )
                }
            )
        }

        /*
         * ==========================================
         * 红色可视化开关
         * ==========================================
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    text = "红色可视化"
                )

                Text(
                    text = "让 Overlay 显示为红色"
                )
            }

            Switch(
                /*
                 * 默认 OFF
                 */
                checked =
                    redVisualizationEnabled,

                /*
                 * Overlay OFF：
                 *
                 * 红色开关不可用
                 */
                enabled =
                    overlayEnabled,

                onCheckedChange = { enabled ->

                    redVisualizationEnabled =
                        enabled

                    /*
                     * 通知 AccessibilityService
                     */
                    OverlayController
                        .setRedVisualizationEnabled(
                            enabled
                        )
                }
            )
        }
    }
}

/*
 * ==========================================
 * Preview
 * ==========================================
 */

@Preview(
    showBackground = true
)
@Composable
private fun DefaultPreview() {

    MyApplicationTheme {

        ScanRecordScreen()
    }
}