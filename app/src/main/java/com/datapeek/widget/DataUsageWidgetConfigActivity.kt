package com.datapeek.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.formatBytes
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

class DataUsageWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initialConfig = WidgetConfigStore.read(this, appWidgetId)
        setContent {
            WidgetConfigScreen(
                initialConfig = initialConfig,
                onCancel = ::finish,
                onSave = ::saveConfiguration,
            )
        }
    }

    private fun saveConfiguration(config: WidgetConfig) {
        WidgetConfigStore.save(this, appWidgetId, config)
        lifecycleScope.launch {
            DataUsageWidget().update(
                this@DataUsageWidgetConfigActivity,
                GlanceAppWidgetManager(this@DataUsageWidgetConfigActivity).getGlanceIdBy(appWidgetId),
            )
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    initialConfig: WidgetConfig,
    onCancel: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
) {
    var config by remember { mutableStateOf(initialConfig) }
    val colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("위젯 설정") },
                    navigationIcon = { TextButton(onClick = onCancel) { Text("취소") } },
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("스타일", style = MaterialTheme.typography.titleMedium)
                WidgetOptionRow(
                    label = "정보형",
                    supporting = "잔여량과 사용량을 한눈에 표시",
                    selected = config.style == WidgetStyle.INFO,
                    onClick = { config = config.copy(style = WidgetStyle.INFO) },
                )
                WidgetOptionRow(
                    label = "게이지형",
                    supporting = "반원 게이지와 잔여량을 표시",
                    selected = config.style == WidgetStyle.GAUGE,
                    onClick = { config = config.copy(style = WidgetStyle.GAUGE) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("글자/게이지 색상", style = MaterialTheme.typography.titleMedium)
                WidgetOptionRow(
                    label = widgetThemeLabel(WidgetTheme.SYSTEM),
                    selected = config.theme == WidgetTheme.SYSTEM,
                    onClick = { config = config.copy(theme = WidgetTheme.SYSTEM) },
                )
                WidgetOptionRow(
                    label = widgetThemeLabel(WidgetTheme.LIGHT_FOREGROUND),
                    selected = config.theme == WidgetTheme.LIGHT_FOREGROUND,
                    onClick = { config = config.copy(theme = WidgetTheme.LIGHT_FOREGROUND) },
                )
                WidgetOptionRow(
                    label = widgetThemeLabel(WidgetTheme.DARK_FOREGROUND),
                    selected = config.theme == WidgetTheme.DARK_FOREGROUND,
                    onClick = { config = config.copy(theme = WidgetTheme.DARK_FOREGROUND) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("배경", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = config.backgroundEnabled,
                        onCheckedChange = { enabled -> config = config.copy(backgroundEnabled = enabled) },
                    )
                    Text("위젯 배경 사용")
                }
                if (widgetOpacityControlVisible(config.backgroundEnabled)) {
                    Text(
                        "투명도 ${config.safeOpacityPercent}%  ·  0% 불투명 / 100% 완전 투명",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = config.safeOpacityPercent.toFloat(),
                        onValueChange = { value -> config = config.copy(opacityPercent = value.roundToInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("표시 항목", style = MaterialTheme.typography.titleMedium)
                ToggleRow(
                    label = "새로고침 동작",
                    checked = config.showRefresh,
                    onCheckedChange = { config = config.copy(showRefresh = it) },
                )
                ToggleRow(
                    label = "마지막 업데이트 시간",
                    checked = config.showUpdated,
                    onCheckedChange = { config = config.copy(showUpdated = it) },
                )

                WidgetPreview(config)
                Spacer(Modifier.height(4.dp))
                Button(onClick = { onSave(config) }, modifier = Modifier.fillMaxWidth()) {
                    Text("위젯 저장")
                }
            }
        }
    }
}

@Composable
private fun WidgetOptionRow(
    label: String,
    supporting: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

@Composable
private fun WidgetPreview(config: WidgetConfig) {
    val snapshot = remember {
        UsageSnapshot(
            carrier = Carrier.FREET_SKT,
            totalBytes = 20L * 1024 * 1024 * 1024,
            usedBytes = 315733L * 1024,
            remainingBytes = 20655787L * 1024,
            fetchedAt = Instant.now(),
            source = UsageSource.CARRIER_SERVER,
        )
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val previewPlan = widgetLayoutPlan(180, 82, config.style, config)
    val visualConfig = resolveWidgetVisualConfig(
        config = config,
        systemDark = systemDark,
        gaugeDiameterDp = previewPlan.gaugeDiameterDp,
        sizeClass = previewPlan.sizeClass,
    )
    val previewPrimary = androidx.compose.ui.graphics.Color(visualConfig.primaryTextArgb)
    val previewSecondary = androidx.compose.ui.graphics.Color(visualConfig.secondaryTextArgb)
    val previewBackground = visualConfig.backgroundArgb
        ?.let { androidx.compose.ui.graphics.Color(it) }
        ?: androidx.compose.ui.graphics.Color.Transparent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        color = previewBackground,
        contentColor = previewPrimary,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val plan = previewPlan
            val compact = plan.compactValues
            val remaining = if (compact) formatWidgetMicroBytes(snapshot.remainingBytes) else formatBytes(snapshot.remainingBytes)
            val used = if (compact) formatWidgetMicroBytes(snapshot.usedBytes) else formatBytes(snapshot.usedBytes)
            val total = if (compact) formatWidgetMicroBytes(snapshot.totalBytes) else formatBytes(snapshot.totalBytes)
            Column(modifier = Modifier.padding(16.dp)) {
                Text("미리보기", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (config.style == WidgetStyle.GAUGE) {
                        PreviewGauge(
                            snapshot = snapshot,
                            visualConfig = visualConfig,
                            widthDp = plan.gaugeWidthDp,
                            heightDp = plan.gaugeHeightDp,
                            sizeClass = plan.sizeClass,
                        )
                    }
                    Column(modifier = Modifier.padding(start = if (config.style == WidgetStyle.GAUGE) 8.dp else 0.dp)) {
                        Text(
                            remaining,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = previewPrimary,
                        )
                        if (config.style == WidgetStyle.INFO) {
                            Text("남음", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = previewSecondary)
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text("사용 $used", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = previewSecondary)
                        Text("전체 $total", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = previewSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewGauge(
    snapshot: UsageSnapshot,
    visualConfig: WidgetVisualConfig,
    widthDp: Int,
    heightDp: Int,
    sizeClass: WidgetSizeClass,
) {
    val percent = calculateGaugeRemainingFraction(snapshot.remainingBytes, snapshot.totalBytes) * 100f
    val centerText = gaugeCenterTextPlan(percent, widthDp, sizeClass)
    val trackColor = androidx.compose.ui.graphics.Color(visualConfig.gaugeTrackArgb)
    val progressColor = androidx.compose.ui.graphics.Color(visualConfig.gaugeProgressArgb)
    Box(modifier = Modifier.size(width = widthDp.dp, height = heightDp.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = visualConfig.gaugeStrokeDp.dp.toPx()
            val geometry = calculateGaugeGeometry(
                availableWidth = size.width,
                availableHeight = size.height,
                safePadding = strokeWidth / 2f + 1.dp.toPx(),
            )
            val rect = androidx.compose.ui.geometry.Rect(
                geometry.left,
                geometry.top,
                geometry.right,
                geometry.bottom,
            )
            drawArc(
                color = trackColor,
                startAngle = GAUGE_OPEN_RING_START_ANGLE_DEGREES,
                sweepAngle = GAUGE_OPEN_RING_SWEEP_DEGREES,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = GAUGE_OPEN_RING_START_ANGLE_DEGREES,
                sweepAngle = gaugeProgressSweepDegrees(percent / 100f),
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            centerText.label,
            modifier = Modifier.align(Alignment.Center),
            fontSize = centerText.fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color(visualConfig.primaryTextArgb),
        )
    }
}
