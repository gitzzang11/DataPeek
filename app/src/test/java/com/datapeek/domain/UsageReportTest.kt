package com.datapeek.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageReportTest {
    private val october = cycleFor(LocalDate.parse("2026-10-15"), 1)
    private fun date(day: Int) = LocalDate.parse("2026-10-%02d".format(day))

    @Test fun consecutiveDaysAreMeasuredFromTheDifferenceOfDayEndBalances() {
        val report = buildCycleReport(octoberDaily(1 to 29_000, 2 to 27_500, 3 to 27_000), october)
        assertEquals(listOf(SpanKind.MEASURED, SpanKind.MEASURED, SpanKind.MEASURED), report.spans.map { it.kind })
        assertEquals(listOf<Long?>(1_000, 1_500, 500), report.spans.map { it.usedBytes })
        assertEquals(3_000L, report.usedBytes)
        assertEquals(1_000.0, report.averageDailyBytes!!, 0.001)
    }

    @Test fun usageBeforeTheFirstRecordedDayIsSharedAndMarkedAsEstimate() {
        val report = buildCycleReport(octoberDaily(12 to 19_000), october)
        val span = report.spans.single()
        assertEquals(SpanKind.BACKFILL, span.kind)
        assertEquals(date(1), span.start)
        assertEquals(date(12), span.end)
        assertEquals(11_000L, span.usedBytes)
        assertEquals(11_000.0 / 12, span.perDayBytes!!, 0.001)
    }

    @Test fun daysWithoutAReadingShareTheUsageBetweenTwoReadings() {
        val report = buildCycleReport(octoberDaily(1 to 29_000, 4 to 26_000), october)
        val gap = report.spans.last()
        assertEquals(SpanKind.ESTIMATED, gap.kind)
        assertEquals(date(2), gap.start)
        assertEquals(date(4), gap.end)
        assertEquals(3_000L, gap.usedBytes)
        assertEquals(1_000.0, gap.perDayBytes!!, 0.001)
    }

    @Test fun aBalanceIncreaseIsATopUpAndIsNotCountedAsNegativeUsage() {
        val report = buildCycleReport(octoberDaily(1 to 29_000, 2 to 28_000, 3 to 29_500, 4 to 28_500), october)
        assertEquals(listOf(SpanKind.MEASURED, SpanKind.MEASURED, SpanKind.TOP_UP, SpanKind.MEASURED), report.spans.map { it.kind })
        assertNull(report.spans[2].usedBytes)
        assertEquals(3_000L, report.usedBytes)
    }

    @Test fun firstReadingWithoutAKnownTotalHasNoUsageFigure() {
        val ledger = ledgerOf(reading("2026-10-05T00:00:00Z", 10_000, total = null))
        val span = buildCycleReport(ledger, october).spans.single()
        assertEquals(SpanKind.FIRST, span.kind)
        assertNull(span.usedBytes)
    }

    @Test fun otherMonthsNeverLeakIntoTheReport() {
        val ledger = ledgerOf(
            reading("2026-09-30T00:00:00Z", 5_000),
            reading("2026-10-02T00:00:00Z", 28_000),
            reading("2026-11-01T00:00:00Z", 29_000),
        )
        val report = buildCycleReport(ledger, october)
        assertEquals(1, report.spans.size)
        assertEquals(2_000L, report.usedBytes)
    }
}
