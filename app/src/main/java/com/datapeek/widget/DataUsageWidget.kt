package com.datapeek.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.datapeek.MainActivity
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.repository.UsageCacheStore
import com.datapeek.data.util.calculateRemainingPercent
import com.datapeek.data.util.daysUntilReset
import com.datapeek.data.util.formatBytes
import com.datapeek.data.util.resetLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class DataUsageWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = UsageCacheStore.readSnapshot(context)
        val lastError = UsageCacheStore.readError(context)
        val isRefreshing = UsageCacheStore.readRefreshing(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore.read(context, appWidgetId)
        provideContent {
            WidgetContent(
                snapshot = snapshot,
                lastError = lastError,
                isRefreshing = isRefreshing,
                config = config,
                appWidgetId = appWidgetId,
            )
        }
    }
}

internal data class WidgetPalette(
    val primaryText: Int,
    val secondaryText: Int,
    val accent: Int,
    val track: Int,
    val gaugeStrokeDp: Float,
)

@Composable
private fun WidgetContent(
    snapshot: UsageSnapshot?,
    lastError: String?,
    isRefreshing: Boolean,
    config: WidgetConfig,
    appWidgetId: Int?,
) {
    val size = LocalSize.current
    val widthDp = size.width.value.roundToInt()
    val heightDp = size.height.value.roundToInt()
    val layoutPlan = widgetLayoutPlan(widthDp, heightDp, config.style, config)
    val context = LocalContext.current
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val visualConfig = remember(config, systemDark, layoutPlan.gaugeDiameterDp, layoutPlan.sizeClass) {
        resolveWidgetVisualConfig(
            config = config,
            systemDark = systemDark,
            gaugeDiameterDp = layoutPlan.gaugeDiameterDp,
            sizeClass = layoutPlan.sizeClass,
        )
    }
    SideEffect {
        appWidgetId?.let {
            WidgetSizeDiagnosticsStore.save(
                context = context,
                appWidgetId = it,
                widthDp = widthDp,
                heightDp = heightDp,
                layoutPlan = layoutPlan,
                config = config,
                visualConfig = visualConfig,
            )
        }
    }
    val palette = WidgetPalette(
        primaryText = visualConfig.primaryTextArgb,
        secondaryText = visualConfig.secondaryTextArgb,
        accent = visualConfig.gaugeProgressArgb,
        track = visualConfig.gaugeTrackArgb,
        gaugeStrokeDp = visualConfig.gaugeStrokeDp,
    )
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        if (widgetRequiresLogin(lastError)) {
            putExtra(MainActivity.EXTRA_OPEN_LOGIN, true)
        }
    }
    val openAppAction = actionStartActivity(openAppIntent)
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .then(
            if (visualConfig.renderBackground) {
                GlanceModifier
                    .background(widgetColorProvider(requireNotNull(visualConfig.backgroundArgb)))
                    .cornerRadius(24.dp)
                    .appWidgetBackground()
            } else {
                GlanceModifier
            },
        )
        .padding(layoutPlan.typography.contentPaddingDp.dp)
        .clickable(openAppAction)

    Column(
        modifier = rootModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (snapshot == null) {
            NoSnapshotContent(layoutPlan, lastError, palette)
        } else if (config.style == WidgetStyle.GAUGE) {
            GaugeContent(snapshot, lastError, isRefreshing, layoutPlan, palette, appWidgetId)
        } else {
            InfoContent(snapshot, lastError, isRefreshing, layoutPlan, palette, appWidgetId)
        }
    }
}

@Composable
private fun NoSnapshotContent(layoutPlan: WidgetLayoutPlan, lastError: String?, palette: WidgetPalette) {
    val isMicro = layoutPlan.kind == WidgetLayoutKind.MICRO
    Text(
        if (isMicro) "로그인" else widgetStatusLabel(lastError) ?: "로그인 필요",
        style = widgetTextStyle(palette.primaryText, if (isMicro) 13 else layoutPlan.typography.titleSp),
        maxLines = 1,
    )
    if (!isMicro) {
        Text("탭하여 DataPeek 열기", style = widgetTextStyle(palette.secondaryText, 11), maxLines = 1)
    }
}

@Composable
private fun InfoContent(
    snapshot: UsageSnapshot,
    lastError: String?,
    isRefreshing: Boolean,
    layoutPlan: WidgetLayoutPlan,
    palette: WidgetPalette,
    appWidgetId: Int?,
) {
    val plan = layoutPlan.contentPlan
    val type = layoutPlan.typography
    val remaining = widgetDisplayValue(snapshot.remainingBytes, snapshot.remainingUnit, layoutPlan)
    when (layoutPlan.kind) {
        WidgetLayoutKind.MICRO -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                remaining,
                style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true),
                maxLines = 1,
            )
            if (widgetRequiresLogin(lastError)) {
                Text("로그인 필요", style = widgetTextStyle(palette.secondaryText, 8), maxLines = 1)
            }
        }
        WidgetLayoutKind.COMPACT_WIDE_DASHBOARD -> CompactWideInfoContent(snapshot, lastError, layoutPlan, palette)
        WidgetLayoutKind.WIDE_DASHBOARD -> if (layoutPlan.heightClass == WidgetHeightClass.H1) {
            WideShortInfoContent(snapshot, lastError, isRefreshing, layoutPlan, palette)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("남음", style = widgetTextStyle(palette.secondaryText, type.titleSp), maxLines = 1)
                    Text(remaining, style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true), maxLines = 1)
                }
                Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                Column {
                    if (plan.showUsed) MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
                    if (plan.showTotal) MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
                    OptionalInfo(snapshot, lastError, isRefreshing, plan, palette, appWidgetId, type)
                }
            }
        }
        WidgetLayoutKind.TALL_DASHBOARD -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("남음", style = widgetTextStyle(palette.secondaryText, type.titleSp), maxLines = 1)
            Text(remaining, style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true), maxLines = 1)
            Spacer(GlanceModifier.height(type.itemSpacingDp.dp))
            if (plan.showUsed) MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
            if (plan.showTotal) MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
            OptionalInfo(snapshot, lastError, isRefreshing, plan, palette, appWidgetId, type)
        }
        WidgetLayoutKind.COMPACT_DETAIL,
        WidgetLayoutKind.FULL_DETAIL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("데이터", style = widgetTextStyle(palette.secondaryText, type.titleSp), maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            Text("남음", style = widgetTextStyle(palette.secondaryText, type.tertiarySp), maxLines = 1)
            Text(remaining, style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true), maxLines = 1)
            if (layoutPlan.kind == WidgetLayoutKind.FULL_DETAIL) {
                Spacer(GlanceModifier.height(type.itemSpacingDp.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        if (plan.showUsed) MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
                        if (plan.showTotal) MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
                    }
                    Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                    Column {
                        OptionalInfo(snapshot, lastError, isRefreshing, plan, palette, appWidgetId, type)
                    }
                }
            } else {
                if (plan.showUsed) MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
                if (plan.showTotal) MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
                OptionalInfo(snapshot, lastError, isRefreshing, plan, palette, appWidgetId, type)
            }
        }
    }
}

@Composable
private fun CompactWideInfoContent(
    snapshot: UsageSnapshot,
    lastError: String?,
    layoutPlan: WidgetLayoutPlan,
    palette: WidgetPalette,
) {
    val type = layoutPlan.typography
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                widgetDisplayValue(snapshot.remainingBytes, snapshot.remainingUnit, layoutPlan),
                style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true),
                maxLines = 1,
            )
            Text("남음", style = widgetTextStyle(palette.secondaryText, type.tertiarySp), maxLines = 1)
        }
        Spacer(GlanceModifier.width(8.dp))
        Column {
            MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
            MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
            if (widgetRequiresLogin(lastError)) {
                Text("로그인 필요", style = widgetTextStyle(palette.secondaryText, type.tertiarySp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun WideShortInfoContent(
    snapshot: UsageSnapshot,
    lastError: String?,
    isRefreshing: Boolean,
    layoutPlan: WidgetLayoutPlan,
    palette: WidgetPalette,
) {
    val plan = layoutPlan.contentPlan
    val type = layoutPlan.typography
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${widgetDisplayValue(snapshot.remainingBytes, snapshot.remainingUnit, layoutPlan)} 남음",
            style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true),
            maxLines = 1,
        )
        if (plan.showUsed) {
            Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
            MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
        }
        if (plan.showTotal) {
            Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
            MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
        }
        if (plan.showPercent) {
            remainingPercent(snapshot)?.let {
                Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                Text(formatWidgetPercent(it), style = widgetTextStyle(palette.secondaryText, type.tertiarySp), maxLines = 1)
            }
        }
        if (plan.showUpdated) {
            Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
            Text(
                snapshot.fetchedAt.atZone(ZoneId.systemDefault()).format(TIME_FORMATTER),
                style = widgetTextStyle(palette.secondaryText, type.tertiarySp),
                maxLines = 1,
            )
        }
        if (plan.showRefresh) {
            Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
            RefreshGlyph(isRefreshing, palette, type.tertiarySp)
        }
        if (widgetRequiresLogin(lastError)) {
            Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
            Text("로그인 필요", style = widgetTextStyle(palette.secondaryText, type.tertiarySp), maxLines = 1)
        }
    }
}

@Composable
private fun MetricText(label: String, value: String, palette: WidgetPalette, sizeSp: Int) {
    Text("$label $value", style = widgetTextStyle(palette.secondaryText, sizeSp), maxLines = 1)
}

@Composable
private fun OptionalInfo(
    snapshot: UsageSnapshot,
    lastError: String?,
    isRefreshing: Boolean,
    plan: WidgetContentPlan,
    palette: WidgetPalette,
    appWidgetId: Int?,
    typography: WidgetTypography,
) {
    if (plan.showPercent) {
        remainingPercent(snapshot)?.let {
            Text("잔여 ${formatWidgetPercent(it)}", style = widgetTextStyle(palette.secondaryText, typography.tertiarySp), maxLines = 1)
        }
    }
    if (plan.showReset) {
        (snapshot.daysUntilReset ?: daysUntilReset(snapshot.cycleEnd))?.let { days ->
            Text(resetLabel(days), style = widgetTextStyle(palette.secondaryText, typography.tertiarySp), maxLines = 1)
        }
    }
    if (plan.showUpdated) UpdatedText(snapshot, palette, typography.tertiarySp)
    if (plan.showStatus) {
        Text(widgetStateLabel(lastError, isRefreshing), style = widgetTextStyle(palette.secondaryText, typography.tertiarySp), maxLines = 1)
    } else if (widgetRequiresLogin(lastError)) {
        Text("로그인 필요", style = widgetTextStyle(palette.secondaryText, typography.tertiarySp), maxLines = 1)
    }
    if (plan.showCarrier) {
        Text(snapshot.carrier.label, style = widgetTextStyle(palette.secondaryText, typography.tertiarySp), maxLines = 1)
    }
    if (plan.showRefresh) RefreshText(isRefreshing, palette, typography.tertiarySp)
    if (plan.showSettings) SettingsText(appWidgetId, palette, typography.tertiarySp)
}

@Composable
private fun GaugeContent(
    snapshot: UsageSnapshot,
    lastError: String?,
    isRefreshing: Boolean,
    layoutPlan: WidgetLayoutPlan,
    palette: WidgetPalette,
    appWidgetId: Int?,
) {
    val percent = calculateGaugeRemainingFraction(snapshot.remainingBytes, snapshot.totalBytes) * 100f
    val plan = layoutPlan.contentPlan
    val type = layoutPlan.typography
    val remaining = widgetDisplayValue(snapshot.remainingBytes, snapshot.remainingUnit, layoutPlan)
    val centerText = gaugeCenterTextPlan(percent, layoutPlan.gaugeDiameterDp, layoutPlan.sizeClass)
    when {
        layoutPlan.kind == WidgetLayoutKind.MICRO -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GaugeVisual(percent, palette, layoutPlan.gaugeWidthDp, layoutPlan.gaugeHeightDp, centerText)
            Text(remaining, style = widgetTextStyle(palette.primaryText, type.metricSp, bold = true), maxLines = 1)
            if (widgetRequiresLogin(lastError)) {
                Text("로그인 필요", style = widgetTextStyle(palette.secondaryText, 8), maxLines = 1)
            }
        }
        layoutPlan.gaugePlacement == GaugePlacement.START -> Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GaugeVisual(
                percent,
                palette,
                layoutPlan.gaugeWidthDp,
                layoutPlan.gaugeHeightDp,
                centerText,
            )
            Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
            if (layoutPlan.heightClass == WidgetHeightClass.H1) {
                WideShortGaugeMetrics(snapshot, lastError, isRefreshing, layoutPlan, palette)
            } else {
                Column {
                    Text("남음", style = widgetTextStyle(palette.secondaryText, type.titleSp), maxLines = 1)
                    Text(remaining, style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true), maxLines = 1)
                    if (plan.showUsed) MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
                    if (plan.showTotal) MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
                    OptionalInfo(snapshot, lastError, isRefreshing, plan.copy(showPercent = false), palette, appWidgetId, type)
                }
            }
        }
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GaugeVisual(
                percent,
                palette,
                layoutPlan.gaugeWidthDp,
                layoutPlan.gaugeHeightDp,
                centerText,
            )
            Text(remaining, style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true), maxLines = 1)
            if (plan.showUsed || plan.showTotal) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        if (plan.showUsed) MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.metricSp)
                    }
                    Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                    Column {
                        if (plan.showTotal) MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.metricSp)
                    }
                }
            }
            OptionalInfo(snapshot, lastError, isRefreshing, plan.copy(showPercent = false), palette, appWidgetId, type)
        }
    }
}

@Composable
private fun WideShortGaugeMetrics(
    snapshot: UsageSnapshot,
    lastError: String?,
    isRefreshing: Boolean,
    layoutPlan: WidgetLayoutPlan,
    palette: WidgetPalette,
) {
    val plan = layoutPlan.contentPlan
    val type = layoutPlan.typography
    if (layoutPlan.kind == WidgetLayoutKind.COMPACT_WIDE_DASHBOARD) {
        Column {
            Text(
                widgetDisplayValue(snapshot.remainingBytes, snapshot.remainingUnit, layoutPlan),
                style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true),
                maxLines = 1,
            )
            MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.tertiarySp)
            MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.tertiarySp)
            if (widgetRequiresLogin(lastError)) {
                Text("로그인 필요", style = widgetTextStyle(palette.secondaryText, type.tertiarySp), maxLines = 1)
            }
        }
        return
    }
    Column {
        Text(
            "${widgetDisplayValue(snapshot.remainingBytes, snapshot.remainingUnit, layoutPlan)} 남음",
            style = widgetTextStyle(palette.primaryText, type.remainingSp, bold = true),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (plan.showUsed) {
                MetricText("사용", widgetDisplayValue(snapshot.usedBytes, snapshot.usedUnit, layoutPlan), palette, type.tertiarySp)
            }
            if (plan.showTotal) {
                Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                MetricText("전체", widgetDisplayValue(snapshot.totalBytes, snapshot.totalUnit, layoutPlan), palette, type.tertiarySp)
            }
            if (plan.showUpdated) {
                Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                Text(
                    snapshot.fetchedAt.atZone(ZoneId.systemDefault()).format(TIME_FORMATTER),
                    style = widgetTextStyle(palette.secondaryText, type.tertiarySp),
                    maxLines = 1,
                )
            }
            if (plan.showRefresh) {
                Spacer(GlanceModifier.width(type.itemSpacingDp.dp))
                RefreshGlyph(isRefreshing, palette, type.tertiarySp)
            }
        }
    }
}

@Composable
private fun GaugeVisual(
    percent: Float,
    palette: WidgetPalette,
    widthDp: Int,
    heightDp: Int,
    centerText: GaugeCenterTextPlan,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bitmapWidth = (widthDp * density).roundToInt().coerceIn(1, 960)
    val bitmapHeight = (heightDp * density).roundToInt().coerceIn(1, 480)
    val strokeWidthPx = palette.gaugeStrokeDp * density
    val bitmap = remember(bitmapWidth, bitmapHeight, percent, palette, strokeWidthPx) {
        renderOpenRingGaugeBitmap(bitmapWidth, bitmapHeight, percent, palette, strokeWidthPx)
    }
    bitmap.density = context.resources.displayMetrics.densityDpi
    Box(
        modifier = GlanceModifier.width(widthDp.dp).height(heightDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "잔여 데이터 ${formatWidgetPercent(percent)}",
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Text(
            centerText.label,
            style = widgetTextStyle(palette.primaryText, centerText.fontSizeSp, bold = true),
            maxLines = 1,
        )
    }
}

@Composable
private fun UpdatedText(snapshot: UsageSnapshot, palette: WidgetPalette, sizeSp: Int) {
    Text(
        "업데이트 ${snapshot.fetchedAt.atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)}",
        style = widgetTextStyle(palette.secondaryText, sizeSp),
        maxLines = 1,
    )
}

@Composable
private fun RefreshText(isRefreshing: Boolean, palette: WidgetPalette, sizeSp: Int) {
    Text(
        widgetRefreshLabel(isRefreshing),
        modifier = GlanceModifier.padding(top = 6.dp).then(
            if (isRefreshing) GlanceModifier else GlanceModifier.clickable(actionRunCallback<WidgetRefreshAction>()),
        ),
        style = widgetTextStyle(palette.accent, sizeSp),
        maxLines = 1,
    )
}

@Composable
private fun RefreshGlyph(isRefreshing: Boolean, palette: WidgetPalette, sizeSp: Int) {
    Text(
        if (isRefreshing) "…" else "↻",
        modifier = if (isRefreshing) GlanceModifier else GlanceModifier.clickable(actionRunCallback<WidgetRefreshAction>()),
        style = widgetTextStyle(palette.accent, sizeSp),
        maxLines = 1,
    )
}

@Composable
private fun SettingsText(appWidgetId: Int?, palette: WidgetPalette, sizeSp: Int) {
    val id = appWidgetId ?: return
    val intent = Intent(LocalContext.current, DataUsageWidgetConfigActivity::class.java)
        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    Text(
        "설정",
        modifier = GlanceModifier.padding(top = 4.dp).clickable(actionStartActivity(intent)),
        style = widgetTextStyle(palette.accent, sizeSp),
        maxLines = 1,
    )
}

private fun widgetTextStyle(color: Int, sizeSp: Int, bold: Boolean = false): TextStyle = TextStyle(
    color = widgetColorProvider(color),
    fontSize = sizeSp.sp,
    fontWeight = if (bold) androidx.glance.text.FontWeight.Bold else androidx.glance.text.FontWeight.Normal,
)

private fun widgetColorProvider(argb: Int): androidx.glance.unit.ColorProvider =
    ColorProvider(day = Color(argb), night = Color(argb))

internal fun widgetRefreshLabel(isRefreshing: Boolean): String =
    if (isRefreshing) "새로고침 중…" else "새로고침"

internal fun widgetStatusLabel(lastError: String?): String? = lastError?.let {
    if (it.contains("로그인") || it.contains("세션")) "로그인 필요" else "업데이트 실패"
}

internal fun widgetRequiresLogin(lastError: String?): Boolean =
    lastError?.contains("로그인") == true || lastError?.contains("세션") == true

internal fun widgetStateLabel(lastError: String?, isRefreshing: Boolean): String = when {
    isRefreshing -> "새로고침 중"
    lastError != null -> widgetStatusLabel(lastError) ?: "업데이트 실패"
    else -> "정상"
}

private fun widgetDisplayValue(
    bytes: Long?,
    preferredUnit: com.datapeek.data.util.DataUnit?,
    layoutPlan: WidgetLayoutPlan,
): String = if (layoutPlan.compactValues) {
    formatWidgetMicroBytes(bytes, preferredUnit)
} else {
    formatBytes(bytes, preferredUnit)
}

private fun remainingPercent(snapshot: UsageSnapshot): Float? =
    calculateRemainingPercent(snapshot.remainingBytes, snapshot.totalBytes)?.coerceIn(0f, 100f)

internal fun formatWidgetPercent(value: Float): String {
    val safe = value.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
    val rounded = safe.roundToInt()
    return if (abs(safe - rounded) < 0.05f) "$rounded%" else "${(safe * 10f).roundToInt() / 10f}%"
}

internal fun renderOpenRingGaugeBitmap(
    width: Int,
    height: Int,
    remainingPercent: Float,
    palette: WidgetPalette,
    requestedStrokeWidthPx: Float,
): Bitmap {
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val shortestSide = min(width, height).toFloat()
    val maxStrokeWidth = min(18f, (shortestSide - 0.5f).coerceAtLeast(0.25f))
    val strokeWidth = requestedStrokeWidthPx
        .takeIf { it.isFinite() }
        ?.coerceIn(0.25f, (shortestSide - 0.5f).coerceAtLeast(0.25f))
        ?: maxStrokeWidth
    val safePadding = min(strokeWidth / 2f + 2f, shortestSide / 2f - 0.25f).coerceAtLeast(0f)
    val geometry = calculateGaugeGeometry(
        availableWidth = width.toFloat(),
        availableHeight = height.toFloat(),
        safePadding = safePadding,
    )
    val arcRect = RectF(geometry.left, geometry.top, geometry.right, geometry.bottom)
    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.track
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    val safePercent = remainingPercent.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
    canvas.drawArc(
        arcRect,
        GAUGE_OPEN_RING_START_ANGLE_DEGREES,
        GAUGE_OPEN_RING_SWEEP_DEGREES,
        false,
        trackPaint,
    )
    canvas.drawArc(
        arcRect,
        GAUGE_OPEN_RING_START_ANGLE_DEGREES,
        gaugeProgressSweepDegrees(safePercent / 100f),
        false,
        progressPaint,
    )
    return bitmap
}

internal fun gaugeSweepDegrees(remainingPercent: Float): Float =
    remainingPercent.takeIf { it.isFinite() }
        ?.coerceIn(0f, 100f)
        ?.times(GAUGE_OPEN_RING_SWEEP_DEGREES / 100f)
        ?: 0f

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        UsageCacheStore.markBackgroundRefreshDue(context)
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_TRIGGER_REFRESH, true)
            },
        )
    }
}
