package com.datapeek.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGaugePriorityTest {
    private val config = WidgetConfig(showUpdated = true)

    @Test
    fun everyGaugeLayoutKeepsPercentageVisible() {
        val representativeSizes = listOf(
            82 to 82,
            180 to 82,
            82 to 180,
            180 to 180,
            280 to 180,
            180 to 280,
            360 to 360,
            540 to 540,
        )

        representativeSizes.forEach { (widthDp, heightDp) ->
            val plan = widgetLayoutPlan(widthDp, heightDp, WidgetStyle.GAUGE, config)
            assertTrue("$widthDp x $heightDp must retain the gauge percentage", plan.contentPlan.showPercent)
        }
    }

    @Test
    fun infoLayoutDoesNotInheritTheGaugeOnlyPercentageRule() {
        val micro = widgetLayoutPlan(82, 82, WidgetStyle.INFO, config)
        val compactWide = widgetLayoutPlan(180, 82, WidgetStyle.INFO, config)

        assertFalse(micro.contentPlan.showPercent)
        assertFalse(compactWide.contentPlan.showPercent)
    }

    @Test
    fun gaugeInformationAppearsInPriorityOrderAsSpaceGrows() {
        val oneByOne = widgetLayoutPlan(82, 82, WidgetStyle.GAUGE, config).contentPlan
        assertTrue(oneByOne.showPercent)
        assertTrue(oneByOne.showRemaining)
        assertFalse(oneByOne.showUsed)
        assertFalse(oneByOne.showTotal)
        assertFalse(oneByOne.showUpdated)

        val twoByOne = widgetLayoutPlan(180, 82, WidgetStyle.GAUGE, config).contentPlan
        assertTrue(twoByOne.showPercent)
        assertTrue(twoByOne.showRemaining)
        assertTrue(twoByOne.showUsed)
        assertTrue(twoByOne.showTotal)

        val oneByTwo = widgetLayoutPlan(82, 180, WidgetStyle.GAUGE, config).contentPlan
        assertTrue(oneByTwo.showPercent)
        assertTrue(oneByTwo.showRemaining)
        assertTrue(oneByTwo.showUsed)
        assertTrue(oneByTwo.showTotal)

        val twoByTwo = widgetLayoutPlan(180, 180, WidgetStyle.GAUGE, config).contentPlan
        assertTrue(twoByTwo.showPercent)
        assertTrue(twoByTwo.showRemaining)
        assertTrue(twoByTwo.showUsed)
        assertTrue(twoByTwo.showTotal)

        val large = widgetLayoutPlan(360, 360, WidgetStyle.GAUGE, config).contentPlan
        assertTrue(large.showPercent)
        assertTrue(large.showRemaining)
        assertTrue(large.showUsed)
        assertTrue(large.showTotal)
        assertTrue(large.showUpdated)
    }

    @Test
    fun centerPercentageHandlesEveryEdgeValueAndShrinksForOneHundred() {
        assertEquals("0%", gaugeCenterTextPlan(0f, 44, WidgetSizeClass.MICRO).label)
        assertEquals("1%", gaugeCenterTextPlan(1f, 44, WidgetSizeClass.MICRO).label)
        assertEquals("50%", gaugeCenterTextPlan(50f, 44, WidgetSizeClass.MICRO).label)
        assertEquals("99%", gaugeCenterTextPlan(99f, 44, WidgetSizeClass.MICRO).label)
        assertEquals("100%", gaugeCenterTextPlan(100f, 44, WidgetSizeClass.MICRO).label)

        val ninetyEight = gaugeCenterTextPlan(98f, 44, WidgetSizeClass.MICRO)
        val oneHundred = gaugeCenterTextPlan(100f, 44, WidgetSizeClass.MICRO)
        assertTrue(ninetyEight.fontSizeSp > 12)
        assertTrue(oneHundred.fontSizeSp >= 12)
        assertTrue(oneHundred.fontSizeSp < ninetyEight.fontSizeSp)
    }

    @Test
    fun centerPercentageUsesRawByteFractionAndClampsSafely() {
        val edgeCases = listOf(
            Triple(0L, 100L, "0%"),
            Triple(1L, 100L, "1%"),
            Triple(50L, 100L, "50%"),
            Triple(99L, 100L, "99%"),
            Triple(100L, 100L, "100%"),
            Triple(120L, 100L, "100%"),
            Triple(-10L, 100L, "0%"),
        )

        edgeCases.forEach { (remaining, total, expected) ->
            val percent = calculateGaugeRemainingFraction(remaining, total) * 100f
            assertEquals(expected, gaugeCenterTextPlan(percent, 82, WidgetSizeClass.SMALL).label)
        }
    }
}
