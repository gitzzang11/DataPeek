package com.datapeek.widget

import androidx.glance.appwidget.SizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigurationTest {
    @Test
    fun defaultConfigurationIsInformationSystemThemedAndOpaque() {
        val config = WidgetConfig()

        assertEquals(WidgetStyle.INFO, config.style)
        assertEquals(WidgetTheme.SYSTEM, config.theme)
        assertTrue(config.backgroundEnabled)
        assertEquals(0, config.opacityPercent)
        assertTrue(config.showRefresh)
        assertTrue(config.showUpdated)
    }

    @Test
    fun opacityIsClampedToTheDocumentedRange() {
        assertEquals(0, WidgetConfig(opacityPercent = -1).safeOpacityPercent)
        assertEquals(100, WidgetConfig(opacityPercent = 101).safeOpacityPercent)
        assertEquals(50, WidgetConfig(opacityPercent = 50).safeOpacityPercent)
    }

    @Test
    fun widgetPreferenceKeysAreScopedByAppWidgetId() {
        assertNotEquals(widgetPreferenceKey(101, "style"), widgetPreferenceKey(102, "style"))
        assertNotEquals(widgetPreferenceKey(101, "style"), widgetPreferenceKey(101, "theme"))
    }

    @Test
    fun supportedStylesAndThemesAreExplicit() {
        assertEquals(listOf(WidgetStyle.INFO, WidgetStyle.GAUGE), WidgetStyle.entries)
        assertEquals(
            listOf(WidgetTheme.SYSTEM, WidgetTheme.LIGHT_FOREGROUND, WidgetTheme.DARK_FOREGROUND),
            WidgetTheme.entries,
        )
    }

    @Test
    fun legacyStoredThemesMigrateWithoutChangingTheirVisibleMeaning() {
        assertEquals(WidgetTheme.SYSTEM, parseStoredWidgetTheme("SYSTEM"))
        assertEquals(WidgetTheme.LIGHT_FOREGROUND, parseStoredWidgetTheme("DARK"))
        assertEquals(WidgetTheme.DARK_FOREGROUND, parseStoredWidgetTheme("LIGHT"))
        assertEquals(WidgetTheme.LIGHT_FOREGROUND, parseStoredWidgetTheme("LIGHT_FOREGROUND"))
        assertEquals(WidgetTheme.DARK_FOREGROUND, parseStoredWidgetTheme("DARK_FOREGROUND"))
        assertEquals(WidgetTheme.SYSTEM, parseStoredWidgetTheme("unknown"))
    }

    @Test
    fun savedWidgetConfigurationReopensWithEveryVisualAndContentSetting() {
        val expected = WidgetConfig(
            style = WidgetStyle.GAUGE,
            theme = WidgetTheme.LIGHT_FOREGROUND,
            backgroundEnabled = true,
            opacityPercent = 50,
            showRefresh = false,
            showUpdated = false,
        )

        assertEquals(expected, decodeWidgetConfig(encodeWidgetConfig(expected)))
    }
}

class WidgetResponsiveLayoutTest {
    private val config = WidgetConfig()

    @Test
    fun widgetUsesTheExactLauncherProvidedDpSize() {
        assertEquals(SizeMode.Exact, DataUsageWidget().sizeMode)
    }

    @Test
    fun actualWidgetSizeMapsToFiveResponsiveClasses() {
        assertEquals(WidgetSizeClass.MICRO, classifyWidgetSize(110, 110))
        assertEquals(WidgetSizeClass.SMALL, classifyWidgetSize(180, 120))
        assertEquals(WidgetSizeClass.SMALL, classifyWidgetSize(82, 180))
        assertEquals(WidgetSizeClass.MEDIUM, classifyWidgetSize(180, 180))
        assertEquals(WidgetSizeClass.MEDIUM, classifyWidgetSize(250, 180))
        assertEquals(WidgetSizeClass.LARGE, classifyWidgetSize(360, 220))
        assertEquals(WidgetSizeClass.XLARGE, classifyWidgetSize(500, 360))
    }

    @Test
    fun informationWidgetAddsDetailsAsSpaceGrows() {
        val micro = widgetContentPlan(WidgetSizeClass.MICRO, WidgetStyle.INFO, config)
        val small = widgetContentPlan(WidgetSizeClass.SMALL, WidgetStyle.INFO, config)
        val medium = widgetContentPlan(WidgetSizeClass.MEDIUM, WidgetStyle.INFO, config)
        val large = widgetContentPlan(WidgetSizeClass.LARGE, WidgetStyle.INFO, config)
        val xlarge = widgetContentPlan(WidgetSizeClass.XLARGE, WidgetStyle.INFO, config)

        assertTrue(micro.showRemaining)
        assertFalse(micro.showUsed)
        assertTrue(small.showUsed)
        assertTrue(medium.showTotal)
        assertTrue(large.showPercent)
        assertTrue(xlarge.showUpdated)
        assertTrue(xlarge.showRefresh)
    }

    @Test
    fun gaugeWidgetUsesArcAtEverySizeButStillProgressivelyAddsDetails() {
        val micro = widgetContentPlan(WidgetSizeClass.MICRO, WidgetStyle.GAUGE, config)
        val medium = widgetContentPlan(WidgetSizeClass.MEDIUM, WidgetStyle.GAUGE, config)
        val large = widgetContentPlan(WidgetSizeClass.LARGE, WidgetStyle.GAUGE, config)

        assertTrue(micro.showGauge)
        assertTrue(micro.showRemaining)
        assertFalse(micro.showUsed)
        assertTrue(medium.showUsed)
        assertTrue(medium.showTotal)
        assertTrue(medium.showUpdated)
        assertTrue(large.showGauge)
        assertTrue(large.showUsed)
        assertTrue(large.showTotal)
    }

    @Test
    fun optionalRefreshAndUpdatedFieldsFollowInstanceConfiguration() {
        val config = WidgetConfig(showRefresh = false, showUpdated = false)
        val plan = widgetContentPlan(WidgetSizeClass.XLARGE, WidgetStyle.INFO, config)

        assertFalse(plan.showRefresh)
        assertFalse(plan.showUpdated)
    }

    @Test
    fun opacityControlIsOnlyVisibleWhenTheWidgetHasABackground() {
        assertTrue(widgetOpacityControlVisible(backgroundEnabled = true))
        assertFalse(widgetOpacityControlVisible(backgroundEnabled = false))
    }

    @Test
    fun foregroundThemeLabelsExplainTheirContrastBehavior() {
        assertEquals("시스템", widgetThemeLabel(WidgetTheme.SYSTEM))
        assertEquals("밝은 글자", widgetThemeLabel(WidgetTheme.LIGHT_FOREGROUND))
        assertEquals("어두운 글자", widgetThemeLabel(WidgetTheme.DARK_FOREGROUND))
    }
}

class WidgetVisualConfigTest {
    @Test
    fun lightForegroundAndOpaqueBackgroundResolveForTheRealRenderer() {
        val visual = resolveWidgetVisualConfig(
            config = WidgetConfig(
                theme = WidgetTheme.LIGHT_FOREGROUND,
                backgroundEnabled = true,
                opacityPercent = 0,
            ),
            systemDark = false,
            gaugeDiameterDp = 125,
            sizeClass = WidgetSizeClass.MEDIUM,
        )

        assertEquals(ResolvedForegroundMode.LIGHT, visual.foregroundMode)
        assertTrue(visual.renderBackground)
        assertEquals(255, visual.backgroundAlpha)
        assertTrue(visual.backgroundArgb != null)
        assertNotEquals(visual.primaryTextArgb, visual.gaugeProgressArgb)
    }

    @Test
    fun darkForegroundAndHalfTransparentBackgroundResolveForTheRealRenderer() {
        val visual = resolveWidgetVisualConfig(
            config = WidgetConfig(
                theme = WidgetTheme.DARK_FOREGROUND,
                backgroundEnabled = true,
                opacityPercent = 50,
            ),
            systemDark = true,
            gaugeDiameterDp = 125,
            sizeClass = WidgetSizeClass.MEDIUM,
        )

        assertEquals(ResolvedForegroundMode.DARK, visual.foregroundMode)
        assertEquals(128, visual.backgroundAlpha)
        assertEquals(128, visual.backgroundArgb!! ushr 24 and 0xFF)
    }

    @Test
    fun backgroundOffOmitsTheRendererAndIgnoresTransparency() {
        val visual = resolveWidgetVisualConfig(
            config = WidgetConfig(
                backgroundEnabled = false,
                opacityPercent = 25,
            ),
            systemDark = false,
            gaugeDiameterDp = 63,
            sizeClass = WidgetSizeClass.MICRO,
        )

        assertFalse(visual.renderBackground)
        assertEquals(null, visual.backgroundArgb)
        assertEquals(0, visual.backgroundAlpha)
    }

    @Test
    fun transparencyPercentMapsToExpectedBackgroundAlpha() {
        assertEquals(255, resolveBackgroundAlpha(0))
        assertEquals(191, resolveBackgroundAlpha(25))
        assertEquals(128, resolveBackgroundAlpha(50))
        assertEquals(64, resolveBackgroundAlpha(75))
        assertEquals(0, resolveBackgroundAlpha(100))
    }

    @Test
    fun systemForegroundTracksAndroidNightMode() {
        val config = WidgetConfig(theme = WidgetTheme.SYSTEM)
        assertEquals(
            ResolvedForegroundMode.DARK,
            resolveWidgetVisualConfig(config, systemDark = false, 125, WidgetSizeClass.MEDIUM).foregroundMode,
        )
        assertEquals(
            ResolvedForegroundMode.LIGHT,
            resolveWidgetVisualConfig(config, systemDark = true, 125, WidgetSizeClass.MEDIUM).foregroundMode,
        )
    }
}
