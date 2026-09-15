package com.datapeek.widget

import kotlin.math.roundToInt

enum class WidgetStyle {
    INFO,
    GAUGE,
}

enum class WidgetTheme {
    SYSTEM,
    LIGHT_FOREGROUND,
    DARK_FOREGROUND,
}

data class WidgetConfig(
    val style: WidgetStyle = WidgetStyle.INFO,
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    val backgroundEnabled: Boolean = true,
    val opacityPercent: Int = 0,
    val showRefresh: Boolean = true,
    val showUpdated: Boolean = true,
) {
    val safeOpacityPercent: Int
        get() = opacityPercent.coerceIn(0, 100)
}

enum class WidgetSizeClass {
    MICRO,
    SMALL,
    MEDIUM,
    LARGE,
    XLARGE,
}

enum class WidgetWidthClass {
    W1,
    W2,
    W3,
    W4,
    W5,
    W6,
}

enum class WidgetHeightClass {
    H1,
    H2,
    H3,
    H4,
    H5,
    H6,
}

enum class WidgetOrientation {
    COMPACT_SQUARE,
    BALANCED,
    WIDE,
    ULTRA_WIDE,
    TALL,
    ULTRA_TALL,
}

enum class WidgetLayoutKind {
    MICRO,
    COMPACT_WIDE_DASHBOARD,
    WIDE_DASHBOARD,
    TALL_DASHBOARD,
    COMPACT_DETAIL,
    FULL_DETAIL,
}

enum class GaugePlacement {
    TOP,
    START,
}

fun classifyWidgetSize(widthDp: Int, heightDp: Int): WidgetSizeClass = when {
    minOf(widthDp, heightDp) < 140 && maxOf(widthDp, heightDp) < 140 -> WidgetSizeClass.MICRO
    minOf(widthDp, heightDp) < 140 -> WidgetSizeClass.SMALL
    minOf(widthDp, heightDp) < 220 -> WidgetSizeClass.MEDIUM
    maxOf(widthDp, heightDp) < 420 && minOf(widthDp, heightDp) < 280 -> WidgetSizeClass.LARGE
    else -> WidgetSizeClass.XLARGE
}

data class WidgetContentPlan(
    val showRemaining: Boolean,
    val showGauge: Boolean,
    val showUsed: Boolean,
    val showTotal: Boolean,
    val showPercent: Boolean,
    val showUpdated: Boolean,
    val showReset: Boolean,
    val showStatus: Boolean,
    val showCarrier: Boolean,
    val showRefresh: Boolean,
    val showSettings: Boolean,
)

fun widgetContentPlan(
    sizeClass: WidgetSizeClass,
    style: WidgetStyle,
    config: WidgetConfig,
): WidgetContentPlan = when (sizeClass) {
    WidgetSizeClass.MICRO -> WidgetContentPlan(
        showRemaining = true,
        showGauge = style == WidgetStyle.GAUGE,
        showUsed = false,
        showTotal = false,
        showPercent = style == WidgetStyle.GAUGE,
        showUpdated = false,
        showReset = false,
        showStatus = false,
        showCarrier = false,
        showRefresh = false,
        showSettings = false,
    )
    WidgetSizeClass.SMALL -> WidgetContentPlan(
        showRemaining = true,
        showGauge = style == WidgetStyle.GAUGE,
        showUsed = true,
        showTotal = false,
        showPercent = style == WidgetStyle.GAUGE,
        showUpdated = false,
        showReset = false,
        showStatus = false,
        showCarrier = false,
        showRefresh = false,
        showSettings = false,
    )
    WidgetSizeClass.MEDIUM -> WidgetContentPlan(
        showRemaining = true,
        showGauge = style == WidgetStyle.GAUGE,
        showUsed = true,
        showTotal = true,
        showPercent = true,
        showUpdated = config.showUpdated,
        showReset = false,
        showStatus = false,
        showCarrier = false,
        showRefresh = false,
        showSettings = false,
    )
    WidgetSizeClass.LARGE -> WidgetContentPlan(
        showRemaining = true,
        showGauge = style == WidgetStyle.GAUGE,
        showUsed = true,
        showTotal = true,
        showPercent = true,
        showUpdated = config.showUpdated,
        showReset = true,
        showStatus = true,
        showCarrier = false,
        showRefresh = config.showRefresh,
        showSettings = false,
    )
    WidgetSizeClass.XLARGE -> WidgetContentPlan(
        showRemaining = true,
        showGauge = style == WidgetStyle.GAUGE,
        showUsed = true,
        showTotal = true,
        showPercent = true,
        showUpdated = config.showUpdated,
        showReset = true,
        showStatus = true,
        showCarrier = true,
        showRefresh = config.showRefresh,
        showSettings = true,
    )
}

fun classifyWidgetWidth(widthDp: Int): WidgetWidthClass {
    val width = widthDp.coerceAtLeast(0)
    return when {
        width < 120 -> WidgetWidthClass.W1
        width < 200 -> WidgetWidthClass.W2
        width < 300 -> WidgetWidthClass.W3
        width < 400 -> WidgetWidthClass.W4
        width < 520 -> WidgetWidthClass.W5
        else -> WidgetWidthClass.W6
    }
}

fun classifyWidgetHeight(heightDp: Int): WidgetHeightClass {
    val height = heightDp.coerceAtLeast(0)
    return when {
        height < 120 -> WidgetHeightClass.H1
        height < 200 -> WidgetHeightClass.H2
        height < 300 -> WidgetHeightClass.H3
        height < 400 -> WidgetHeightClass.H4
        height < 520 -> WidgetHeightClass.H5
        else -> WidgetHeightClass.H6
    }
}

fun classifyWidgetOrientation(widthDp: Int, heightDp: Int): WidgetOrientation {
    require(widthDp > 0) { "widthDp must be positive" }
    require(heightDp > 0) { "heightDp must be positive" }
    val ratio = widthDp.toFloat() / heightDp.toFloat()
    return when {
        ratio >= 2f -> WidgetOrientation.ULTRA_WIDE
        ratio >= 1.35f -> WidgetOrientation.WIDE
        ratio <= 1f / 2f -> WidgetOrientation.ULTRA_TALL
        ratio <= 1f / 1.35f -> WidgetOrientation.TALL
        maxOf(widthDp, heightDp) < 140 -> WidgetOrientation.COMPACT_SQUARE
        else -> WidgetOrientation.BALANCED
    }
}

data class WidgetLayoutPlan(
    val widthClass: WidgetWidthClass,
    val heightClass: WidgetHeightClass,
    val orientation: WidgetOrientation,
    val sizeClass: WidgetSizeClass,
    val kind: WidgetLayoutKind,
    val contentPlan: WidgetContentPlan,
    val gaugePlacement: GaugePlacement,
    val compactValues: Boolean,
    val gaugeDiameterDp: Int,
    val gaugeWidthDp: Int,
    val gaugeHeightDp: Int,
    val typography: WidgetTypography,
)

data class WidgetTypography(
    val remainingSp: Int,
    val titleSp: Int,
    val metricSp: Int,
    val tertiarySp: Int,
    val contentPaddingDp: Int,
    val itemSpacingDp: Int,
)

fun widgetLayoutPlan(
    widthDp: Int,
    heightDp: Int,
    style: WidgetStyle,
    config: WidgetConfig,
): WidgetLayoutPlan {
    require(widthDp > 0) { "widthDp must be positive" }
    require(heightDp > 0) { "heightDp must be positive" }

    val widthClass = classifyWidgetWidth(widthDp)
    val heightClass = classifyWidgetHeight(heightDp)
    val orientation = classifyWidgetOrientation(widthDp, heightDp)
    val sizeClass = classifyWidgetSize(widthDp, heightDp)
    val kind = when {
        sizeClass == WidgetSizeClass.MICRO -> WidgetLayoutKind.MICRO
        heightClass == WidgetHeightClass.H1 && widthClass == WidgetWidthClass.W2 ->
            WidgetLayoutKind.COMPACT_WIDE_DASHBOARD
        orientation == WidgetOrientation.WIDE || orientation == WidgetOrientation.ULTRA_WIDE ->
            WidgetLayoutKind.WIDE_DASHBOARD
        orientation == WidgetOrientation.TALL || orientation == WidgetOrientation.ULTRA_TALL ->
            WidgetLayoutKind.TALL_DASHBOARD
        sizeClass == WidgetSizeClass.LARGE || sizeClass == WidgetSizeClass.XLARGE ->
            WidgetLayoutKind.FULL_DETAIL
        else -> WidgetLayoutKind.COMPACT_DETAIL
    }

    val baseline = widgetContentPlan(sizeClass, style, config)
    val content = when (kind) {
        WidgetLayoutKind.MICRO -> baseline.copy(
            showUsed = false,
            showTotal = false,
            showPercent = style == WidgetStyle.GAUGE,
            showUpdated = false,
            showReset = false,
            showStatus = false,
            showCarrier = false,
            showRefresh = false,
            showSettings = false,
        )
        WidgetLayoutKind.COMPACT_WIDE_DASHBOARD -> baseline.copy(
            showUsed = true,
            showTotal = true,
            showPercent = style == WidgetStyle.GAUGE,
            showUpdated = false,
            showReset = false,
            showStatus = false,
            showCarrier = false,
            showRefresh = false,
            showSettings = false,
        )
        WidgetLayoutKind.WIDE_DASHBOARD -> baseline.copy(
            showUsed = true,
            showTotal = widthClass.ordinal >= WidgetWidthClass.W3.ordinal,
            showPercent = style == WidgetStyle.GAUGE || widthClass.ordinal >= WidgetWidthClass.W4.ordinal ||
                (widthClass.ordinal >= WidgetWidthClass.W3.ordinal && heightClass.ordinal >= WidgetHeightClass.H2.ordinal),
            showUpdated = config.showUpdated && (
                widthClass.ordinal >= WidgetWidthClass.W4.ordinal ||
                    (widthClass.ordinal >= WidgetWidthClass.W3.ordinal && heightClass.ordinal >= WidgetHeightClass.H2.ordinal)
                ),
            showReset = widthClass.ordinal >= WidgetWidthClass.W5.ordinal && heightClass.ordinal >= WidgetHeightClass.H2.ordinal,
            showStatus = widthClass.ordinal >= WidgetWidthClass.W4.ordinal && heightClass.ordinal >= WidgetHeightClass.H2.ordinal,
            showCarrier = widthClass.ordinal >= WidgetWidthClass.W5.ordinal && heightClass.ordinal >= WidgetHeightClass.H2.ordinal,
            showRefresh = config.showRefresh && (
                widthClass.ordinal >= WidgetWidthClass.W4.ordinal ||
                    (widthClass.ordinal >= WidgetWidthClass.W3.ordinal && heightClass.ordinal >= WidgetHeightClass.H2.ordinal)
                ),
            showSettings = false,
        )
        WidgetLayoutKind.TALL_DASHBOARD -> baseline.copy(
            showUsed = true,
            showTotal = heightClass.ordinal >= WidgetHeightClass.H2.ordinal,
            showPercent = style == WidgetStyle.GAUGE || heightClass.ordinal >= WidgetHeightClass.H3.ordinal,
            showUpdated = config.showUpdated && heightClass.ordinal >= WidgetHeightClass.H3.ordinal,
            showReset = heightClass.ordinal >= WidgetHeightClass.H5.ordinal,
            showStatus = heightClass.ordinal >= WidgetHeightClass.H4.ordinal,
            showCarrier = heightClass.ordinal >= WidgetHeightClass.H5.ordinal,
            showRefresh = config.showRefresh && heightClass.ordinal >= WidgetHeightClass.H3.ordinal,
            showSettings = false,
        )
        WidgetLayoutKind.COMPACT_DETAIL -> baseline.copy(
            showUsed = true,
            showTotal = true,
            showPercent = style == WidgetStyle.GAUGE || (
                widthClass.ordinal >= WidgetWidthClass.W2.ordinal &&
                    heightClass.ordinal >= WidgetHeightClass.H2.ordinal
                ),
            showUpdated = config.showUpdated && heightClass.ordinal >= WidgetHeightClass.H2.ordinal,
            showReset = false,
            showStatus = false,
            showCarrier = false,
            showRefresh = false,
            showSettings = false,
        )
        WidgetLayoutKind.FULL_DETAIL -> baseline.copy(
            showUsed = true,
            showTotal = true,
            showPercent = true,
            showUpdated = config.showUpdated,
            showReset = true,
            showStatus = true,
            showCarrier = minOf(widthClass.ordinal, heightClass.ordinal) >= WidgetWidthClass.W4.ordinal &&
                maxOf(widthClass.ordinal, heightClass.ordinal) >= WidgetWidthClass.W5.ordinal,
            showRefresh = config.showRefresh,
            showSettings = minOf(widthClass.ordinal, heightClass.ordinal) >= WidgetWidthClass.W4.ordinal,
        )
    }
    val gaugePlacement = if (
        style == WidgetStyle.GAUGE &&
        (orientation == WidgetOrientation.WIDE || orientation == WidgetOrientation.ULTRA_WIDE)
    ) {
        GaugePlacement.START
    } else {
        GaugePlacement.TOP
    }
    val compactValues = kind == WidgetLayoutKind.MICRO ||
        kind == WidgetLayoutKind.COMPACT_WIDE_DASHBOARD ||
        (kind == WidgetLayoutKind.WIDE_DASHBOARD && heightClass == WidgetHeightClass.H1) ||
        (kind == WidgetLayoutKind.TALL_DASHBOARD && widthClass == WidgetWidthClass.W1)
    val typography = widgetTypography(widthClass, heightClass, kind)
    val contentWidthDp = (widthDp - typography.contentPaddingDp * 2).coerceAtLeast(1)
    val contentHeightDp = (heightDp - typography.contentPaddingDp * 2).coerceAtLeast(1)
    val gaugeWidthBudget = when (gaugePlacement) {
        GaugePlacement.START -> (contentWidthDp * 0.38f).coerceAtMost(190f)
        GaugePlacement.TOP -> when (kind) {
            WidgetLayoutKind.MICRO -> contentWidthDp.toFloat()
            WidgetLayoutKind.COMPACT_WIDE_DASHBOARD -> contentWidthDp.toFloat()
            WidgetLayoutKind.TALL_DASHBOARD -> contentWidthDp.coerceAtMost(220).toFloat()
            WidgetLayoutKind.COMPACT_DETAIL -> contentWidthDp.coerceAtMost(240).toFloat()
            WidgetLayoutKind.FULL_DETAIL -> contentWidthDp.coerceAtMost(340).toFloat()
            WidgetLayoutKind.WIDE_DASHBOARD -> contentWidthDp.toFloat()
        }
    }
    val gaugeHeightBudget = when (gaugePlacement) {
        GaugePlacement.START -> contentHeightDp * 0.90f
        GaugePlacement.TOP -> when (kind) {
            WidgetLayoutKind.MICRO -> contentHeightDp * 0.58f
            WidgetLayoutKind.COMPACT_WIDE_DASHBOARD -> contentHeightDp * 0.90f
            WidgetLayoutKind.TALL_DASHBOARD -> contentHeightDp * 0.30f
            WidgetLayoutKind.COMPACT_DETAIL -> contentHeightDp * 0.58f
            WidgetLayoutKind.FULL_DETAIL -> contentHeightDp * 0.46f
            WidgetLayoutKind.WIDE_DASHBOARD -> contentHeightDp * 0.60f
        }
    }
    val gaugeDiameterDp = minOf(gaugeWidthBudget, gaugeHeightBudget / GAUGE_OPEN_RING_BITMAP_HEIGHT_RATIO)
        .roundToInt()
        .coerceIn(1, minOf(contentWidthDp, (contentHeightDp / GAUGE_OPEN_RING_BITMAP_HEIGHT_RATIO).roundToInt()))
    val gaugeWidthDp = gaugeDiameterDp
    val gaugeHeightDp = (gaugeDiameterDp * GAUGE_OPEN_RING_BITMAP_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
    return WidgetLayoutPlan(
        widthClass = widthClass,
        heightClass = heightClass,
        orientation = orientation,
        sizeClass = sizeClass,
        kind = kind,
        contentPlan = content,
        gaugePlacement = gaugePlacement,
        compactValues = compactValues,
        gaugeDiameterDp = gaugeDiameterDp,
        gaugeWidthDp = gaugeWidthDp,
        gaugeHeightDp = gaugeHeightDp,
        typography = typography,
    )
}

private fun widgetTypography(
    widthClass: WidgetWidthClass,
    heightClass: WidgetHeightClass,
    kind: WidgetLayoutKind,
): WidgetTypography {
    val shortRank = minOf(widthClass.ordinal, heightClass.ordinal)
    val longRank = maxOf(widthClass.ordinal, heightClass.ordinal)
    val remainingSp = when {
        kind == WidgetLayoutKind.MICRO -> 18
        kind == WidgetLayoutKind.COMPACT_WIDE_DASHBOARD -> 21
        heightClass == WidgetHeightClass.H1 -> if (longRank >= WidgetWidthClass.W4.ordinal) 22 else 19
        widthClass == WidgetWidthClass.W1 -> if (longRank >= WidgetHeightClass.H4.ordinal) 23 else 20
        shortRank >= WidgetWidthClass.W5.ordinal -> 36
        shortRank >= WidgetWidthClass.W4.ordinal -> 33
        shortRank >= WidgetWidthClass.W3.ordinal -> 30
        shortRank >= WidgetWidthClass.W2.ordinal -> 25
        else -> 21
    }
    val padding = when {
        kind == WidgetLayoutKind.MICRO -> 8
        kind == WidgetLayoutKind.COMPACT_WIDE_DASHBOARD -> 8
        heightClass == WidgetHeightClass.H1 || widthClass == WidgetWidthClass.W1 -> 10
        shortRank >= WidgetWidthClass.W4.ordinal -> 18
        shortRank >= WidgetWidthClass.W3.ordinal -> 16
        else -> 12
    }
    return WidgetTypography(
        remainingSp = remainingSp,
        titleSp = (remainingSp / 2).coerceIn(11, 16),
        metricSp = when {
            shortRank >= WidgetWidthClass.W4.ordinal -> 14
            shortRank >= WidgetWidthClass.W2.ordinal -> 13
            else -> 12
        },
        tertiarySp = if (shortRank >= WidgetWidthClass.W3.ordinal) 11 else 10,
        contentPaddingDp = padding,
        itemSpacingDp = when {
            shortRank >= WidgetWidthClass.W4.ordinal -> 10
            shortRank >= WidgetWidthClass.W2.ordinal -> 6
            else -> 4
        },
    )
}

internal fun widgetOpacityControlVisible(backgroundEnabled: Boolean): Boolean = backgroundEnabled

internal fun widgetThemeLabel(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.SYSTEM -> "시스템"
    WidgetTheme.LIGHT_FOREGROUND -> "밝은 글자"
    WidgetTheme.DARK_FOREGROUND -> "어두운 글자"
}
