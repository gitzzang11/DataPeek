package com.datapeek.ui

import com.datapeek.domain.buildCycleReport
import com.datapeek.domain.cycleFor
import com.datapeek.domain.ledgerOf
import com.datapeek.domain.reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageChartDaysTest {
    @Test fun spreadsSpansOverEveryCalendarDayOfTheCycle() {
        val ledger = ledgerOf(reading("2026-10-01T00:00:00Z", 29_000), reading("2026-10-04T00:00:00Z", 26_000))
        val report = buildCycleReport(ledger, cycleFor(LocalDate.parse("2026-10-15"), 1))
        val days = chartDays(report)
        assertEquals(31, days.size)
        assertEquals(1_000.0, days[1].bytes!!, 0.001)
        assertEquals(true, days[1].estimated)
        assertEquals(false, days[0].estimated)
        assertEquals(4_000.0, days[3].cumulativeBytes!!, 0.001)
        assertNull(days[4].bytes)
        assertNull(days[4].cumulativeBytes)
    }
}
