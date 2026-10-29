package com.datapeek.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class SpanKind {
    /** Usage between two consecutive days' readings. */
    MEASURED,

    /** Usage between readings several days apart, shared evenly over those days. */
    ESTIMATED,

    /** Usage from the start of the cycle until the first recorded reading, shared evenly. */
    BACKFILL,

    /** The balance went up (data top-up or plan change), so that day's usage cannot be computed. */
    TOP_UP,

    /** First reading without a known total: nothing to compare with. */
    FIRST,
}

/** [usedBytes] covers every day from [start] to [end]; [remainingBytes] is the balance at the end of [end]. */
data class UsageSpan(
    val start: LocalDate,
    val end: LocalDate,
    val usedBytes: Long?,
    val remainingBytes: Long,
    val kind: SpanKind,
) {
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    val perDayBytes: Double? get() = usedBytes?.let { it.toDouble() / dayCount }
    val isEstimate: Boolean get() = kind == SpanKind.ESTIMATED || kind == SpanKind.BACKFILL
}

data class CycleReport(
    val cycle: UsageCycle,
    val spans: List<UsageSpan>,
    val totalBytes: Long?,
) {
    val usedBytes: Long? get() = spans.mapNotNull { it.usedBytes }.takeIf { it.isNotEmpty() }?.sum()
    val coveredDays: Int get() = spans.filter { it.usedBytes != null }.sumOf { it.dayCount }
    val averageDailyBytes: Double? get() = usedBytes?.let { used -> coveredDays.takeIf { it > 0 }?.let { used.toDouble() / it } }
    val latestRemainingBytes: Long? get() = spans.lastOrNull()?.remainingBytes
}

/** True when the reading follows [previous] without a balance increase or plan-size change. */
private fun LedgerDay.continues(previous: LedgerDay): Boolean =
    !topUpSeen && remainingBytes <= previous.remainingBytes &&
        (totalBytes == null || previous.totalBytes == null || totalBytes == previous.totalBytes)

/** Usage spans of one cycle. Only readings inside [cycle] are used. */
fun buildCycleReport(ledger: UsageLedger, cycle: UsageCycle): CycleReport {
    val readings = ledger.days.filter { it.date in cycle }
    val spans = mutableListOf<UsageSpan>()
    var previous: LedgerDay? = null
    for (day in readings) {
        val before = previous
        spans += when {
            before == null -> {
                val total = day.totalBytes
                if (total != null && !day.topUpSeen) {
                    UsageSpan(
                        cycle.start, day.date, total - day.remainingBytes, day.remainingBytes,
                        if (cycle.start == day.date) SpanKind.MEASURED else SpanKind.BACKFILL,
                    )
                } else {
                    UsageSpan(day.date, day.date, null, day.remainingBytes, if (day.topUpSeen) SpanKind.TOP_UP else SpanKind.FIRST)
                }
            }
            !day.continues(before) -> UsageSpan(day.date, day.date, null, day.remainingBytes, SpanKind.TOP_UP)
            day.date == before.date.plusDays(1) ->
                UsageSpan(day.date, day.date, before.remainingBytes - day.remainingBytes, day.remainingBytes, SpanKind.MEASURED)
            else -> UsageSpan(
                before.date.plusDays(1), day.date, before.remainingBytes - day.remainingBytes,
                day.remainingBytes, SpanKind.ESTIMATED,
            )
        }
        previous = day
    }
    val total = readings.lastOrNull { it.totalBytes != null }?.totalBytes
    return CycleReport(cycle, spans, total)
}

/** The point usage is measured from: either the cycle start (full allowance) or the last top-up reading. */
internal data class UsageChain(val anchorAt: Instant, val anchorRemaining: Long, val readings: List<LedgerDay>)

internal fun usageChain(ledger: UsageLedger, cycle: UsageCycle, zone: ZoneId): UsageChain? {
    val readings = ledger.days.filter { it.date in cycle }
    if (readings.isEmpty()) return null
    var chain = mutableListOf<LedgerDay>()
    var anchorAt = Instant.EPOCH
    var anchorRemaining = 0L
    readings.forEachIndexed { index, day ->
        val broken = if (index == 0) day.topUpSeen else !day.continues(readings[index - 1])
        when {
            index == 0 && !broken && day.totalBytes != null -> {
                anchorAt = cycle.start.atStartOfDay(zone).toInstant()
                anchorRemaining = day.totalBytes
                chain.add(day)
            }
            index == 0 || broken -> {
                chain = mutableListOf(day)
                anchorAt = day.fetchedAt
                anchorRemaining = day.remainingBytes
            }
            else -> chain.add(day)
        }
    }
    return UsageChain(anchorAt, anchorRemaining, chain)
}
