package com.datapeek.widget

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

internal data class WidgetObservedSize(
    val appWidgetId: Int,
    val widthDp: Int,
    val heightDp: Int,
    val sizeClass: WidgetSizeClass,
    val widthClass: WidgetWidthClass = classifyWidgetWidth(widthDp),
    val heightClass: WidgetHeightClass = classifyWidgetHeight(heightDp),
    val orientation: WidgetOrientation = orientationOrBalanced(widthDp, heightDp),
    val selectedLayout: WidgetLayoutKind = WidgetLayoutKind.COMPACT_DETAIL,
    val gaugeDiameterDp: Int = minOf(widthDp, heightDp * 2),
    val style: WidgetStyle = WidgetStyle.INFO,
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    val backgroundEnabled: Boolean = true,
    val transparencyPercent: Int = 0,
    val resolvedForeground: ResolvedForegroundMode = ResolvedForegroundMode.DARK,
    val resolvedPrimaryTextArgb: Int = 0xFF11151C.toInt(),
    val resolvedBackgroundAlpha: Int = 255,
    val gaugeStrokeDp: Float = 0f,
)

internal object WidgetSizeDiagnosticsStore {
    private const val PREFERENCES = "data_usage_widget_size_diagnostics"
    private const val LAST_ID = "last_widget_id"
    private const val LAST_WIDTH = "last_width_dp"
    private const val LAST_HEIGHT = "last_height_dp"
    private const val LAST_WIDTH_CLASS = "last_width_class"
    private const val LAST_HEIGHT_CLASS = "last_height_class"
    private const val LAST_ORIENTATION = "last_orientation"
    private const val LAST_LAYOUT = "last_layout"
    private const val LAST_GAUGE_DIAMETER = "last_gauge_diameter_dp"
    private const val LAST_STYLE = "last_style"
    private const val LAST_THEME = "last_theme"
    private const val LAST_BACKGROUND_ENABLED = "last_background_enabled"
    private const val LAST_TRANSPARENCY = "last_transparency_percent"
    private const val LAST_RESOLVED_FOREGROUND = "last_resolved_foreground"
    private const val LAST_RESOLVED_TEXT = "last_resolved_text_argb"
    private const val LAST_RESOLVED_BACKGROUND_ALPHA = "last_resolved_background_alpha"
    private const val LAST_GAUGE_STROKE = "last_gauge_stroke_dp_bits"

    fun save(
        context: Context,
        appWidgetId: Int,
        widthDp: Int,
        heightDp: Int,
        layoutPlan: WidgetLayoutPlan,
        config: WidgetConfig,
        visualConfig: WidgetVisualConfig,
    ) {
        val safeWidth = widthDp.coerceAtLeast(0)
        val safeHeight = heightDp.coerceAtLeast(0)
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (
            preferences.getInt(LAST_ID, Int.MIN_VALUE) == appWidgetId &&
            preferences.getInt(LAST_WIDTH, Int.MIN_VALUE) == safeWidth &&
            preferences.getInt(LAST_HEIGHT, Int.MIN_VALUE) == safeHeight &&
            preferences.getString(LAST_WIDTH_CLASS, null) == layoutPlan.widthClass.name &&
            preferences.getString(LAST_HEIGHT_CLASS, null) == layoutPlan.heightClass.name &&
            preferences.getString(LAST_ORIENTATION, null) == layoutPlan.orientation.name &&
            preferences.getString(LAST_LAYOUT, null) == layoutPlan.kind.name &&
            preferences.getInt(LAST_GAUGE_DIAMETER, Int.MIN_VALUE) == layoutPlan.gaugeDiameterDp &&
            preferences.getString(LAST_STYLE, null) == config.style.name &&
            preferences.getString(LAST_THEME, null) == config.theme.name &&
            preferences.getBoolean(LAST_BACKGROUND_ENABLED, !config.backgroundEnabled) == config.backgroundEnabled &&
            preferences.getInt(LAST_TRANSPARENCY, Int.MIN_VALUE) == config.safeOpacityPercent &&
            preferences.getString(LAST_RESOLVED_FOREGROUND, null) == visualConfig.foregroundMode.name &&
            preferences.getInt(LAST_RESOLVED_TEXT, Int.MIN_VALUE) == visualConfig.primaryTextArgb &&
            preferences.getInt(LAST_RESOLVED_BACKGROUND_ALPHA, Int.MIN_VALUE) == visualConfig.backgroundAlpha &&
            preferences.getInt(LAST_GAUGE_STROKE, Int.MIN_VALUE) == visualConfig.gaugeStrokeDp.toBits()
        ) {
            return
        }
        preferences.edit {
            putInt(LAST_ID, appWidgetId)
            putInt(LAST_WIDTH, safeWidth)
            putInt(LAST_HEIGHT, safeHeight)
            putString(LAST_WIDTH_CLASS, layoutPlan.widthClass.name)
            putString(LAST_HEIGHT_CLASS, layoutPlan.heightClass.name)
            putString(LAST_ORIENTATION, layoutPlan.orientation.name)
            putString(LAST_LAYOUT, layoutPlan.kind.name)
            putInt(LAST_GAUGE_DIAMETER, layoutPlan.gaugeDiameterDp)
            putString(LAST_STYLE, config.style.name)
            putString(LAST_THEME, config.theme.name)
            putBoolean(LAST_BACKGROUND_ENABLED, config.backgroundEnabled)
            putInt(LAST_TRANSPARENCY, config.safeOpacityPercent)
            putString(LAST_RESOLVED_FOREGROUND, visualConfig.foregroundMode.name)
            putInt(LAST_RESOLVED_TEXT, visualConfig.primaryTextArgb)
            putInt(LAST_RESOLVED_BACKGROUND_ALPHA, visualConfig.backgroundAlpha)
            putInt(LAST_GAUGE_STROKE, visualConfig.gaugeStrokeDp.toBits())
        }
    }

    fun readLatest(context: Context): WidgetObservedSize? {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val id = preferences.getInt(LAST_ID, Int.MIN_VALUE)
        val width = preferences.getInt(LAST_WIDTH, Int.MIN_VALUE)
        val height = preferences.getInt(LAST_HEIGHT, Int.MIN_VALUE)
        if (id == Int.MIN_VALUE || width == Int.MIN_VALUE || height == Int.MIN_VALUE) return null
        val widthClass = preferences.getString(LAST_WIDTH_CLASS, null)
            ?.let { enumValueOrNull<WidgetWidthClass>(it) }
            ?: classifyWidgetWidth(width)
        val heightClass = preferences.getString(LAST_HEIGHT_CLASS, null)
            ?.let { enumValueOrNull<WidgetHeightClass>(it) }
            ?: classifyWidgetHeight(height)
        val orientation = preferences.getString(LAST_ORIENTATION, null)
            ?.let { enumValueOrNull<WidgetOrientation>(it) }
            ?: orientationOrBalanced(width, height)
        val selectedLayout = preferences.getString(LAST_LAYOUT, null)
            ?.let { enumValueOrNull<WidgetLayoutKind>(it) }
            ?: WidgetLayoutKind.COMPACT_DETAIL
        val gaugeDiameter = preferences.getInt(LAST_GAUGE_DIAMETER, minOf(width, height * 2))
        val style = preferences.getString(LAST_STYLE, null)
            ?.let { enumValueOrNull<WidgetStyle>(it) }
            ?: WidgetStyle.INFO
        val theme = preferences.getString(LAST_THEME, null)
            ?.let(::parseStoredWidgetTheme)
            ?: WidgetTheme.SYSTEM
        val backgroundEnabled = preferences.getBoolean(LAST_BACKGROUND_ENABLED, true)
        val transparencyPercent = preferences.getInt(LAST_TRANSPARENCY, 0).coerceIn(0, 100)
        val resolvedForeground = preferences.getString(LAST_RESOLVED_FOREGROUND, null)
            ?.let { enumValueOrNull<ResolvedForegroundMode>(it) }
            ?: ResolvedForegroundMode.DARK
        val resolvedText = preferences.getInt(LAST_RESOLVED_TEXT, 0xFF11151C.toInt())
        val resolvedBackgroundAlpha = preferences.getInt(LAST_RESOLVED_BACKGROUND_ALPHA, 255).coerceIn(0, 255)
        val gaugeStrokeDp = Float.fromBits(preferences.getInt(LAST_GAUGE_STROKE, 0f.toBits()))
        return WidgetObservedSize(
            appWidgetId = id,
            widthDp = width,
            heightDp = height,
            sizeClass = classifyWidgetSize(width, height),
            widthClass = widthClass,
            heightClass = heightClass,
            orientation = orientation,
            selectedLayout = selectedLayout,
            gaugeDiameterDp = gaugeDiameter,
            style = style,
            theme = theme,
            backgroundEnabled = backgroundEnabled,
            transparencyPercent = transparencyPercent,
            resolvedForeground = resolvedForeground,
            resolvedPrimaryTextArgb = resolvedText,
            resolvedBackgroundAlpha = resolvedBackgroundAlpha,
            gaugeStrokeDp = gaugeStrokeDp,
        )
    }

    fun delete(context: Context, appWidgetId: Int) {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getInt(LAST_ID, Int.MIN_VALUE) == appWidgetId) {
            preferences.edit {
                remove(LAST_ID)
                remove(LAST_WIDTH)
                remove(LAST_HEIGHT)
                remove(LAST_WIDTH_CLASS)
                remove(LAST_HEIGHT_CLASS)
                remove(LAST_ORIENTATION)
                remove(LAST_LAYOUT)
                remove(LAST_GAUGE_DIAMETER)
                remove(LAST_STYLE)
                remove(LAST_THEME)
                remove(LAST_BACKGROUND_ENABLED)
                remove(LAST_TRANSPARENCY)
                remove(LAST_RESOLVED_FOREGROUND)
                remove(LAST_RESOLVED_TEXT)
                remove(LAST_RESOLVED_BACKGROUND_ALPHA)
                remove(LAST_GAUGE_STROKE)
            }
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()

private fun orientationOrBalanced(widthDp: Int, heightDp: Int): WidgetOrientation =
    if (widthDp > 0 && heightDp > 0) {
        classifyWidgetOrientation(widthDp, heightDp)
    } else {
        WidgetOrientation.BALANCED
    }

internal fun formatWidgetObservedSize(observed: WidgetObservedSize?): String = observed?.let {
    "Widget ID: ${it.appWidgetId}\n" +
        "Actual: ${it.widthDp} x ${it.heightDp} dp\n" +
        "폭 ${it.widthClass} · 높이 ${it.heightClass}\n" +
        "형태 ${it.orientation} · Layout ${it.selectedLayout}\n" +
        "Style: ${it.style} · Theme: ${it.theme}\n" +
        "Background: ${if (it.backgroundEnabled) "ON" else "OFF"} · Transparency: ${it.transparencyPercent}%\n" +
        "Resolved foreground: ${it.resolvedForeground}\n" +
        "Resolved text: ${formatArgb(it.resolvedPrimaryTextArgb)}\n" +
        "Resolved background alpha: ${it.resolvedBackgroundAlpha}\n" +
        "Gauge diameter: ${it.gaugeDiameterDp} dp · stroke: ${formatDp(it.gaugeStrokeDp)} dp\n" +
        "Gauge arc: ${formatDp(GAUGE_OPEN_RING_START_ANGLE_DEGREES)}° + ${formatDp(GAUGE_OPEN_RING_SWEEP_DEGREES)}°"
} ?: "아직 관찰된 위젯 크기 없음"

private fun formatArgb(argb: Int): String = "#" + argb.toUInt().toString(16).uppercase().padStart(8, '0')

private fun formatDp(value: Float): String = if (value % 1f == 0f) {
    value.toInt().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}
