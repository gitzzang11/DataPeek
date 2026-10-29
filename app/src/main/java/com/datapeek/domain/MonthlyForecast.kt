package com.datapeek.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class ForecastStatus {
    /** Not enough readings in this cycle to estimate a pace yet. */
    INSUFFICIENT,

    /** The balance is already zero. */
    EXHAUSTED,

    /** Nothing was used yet, so nothing can run out. */
    NO_CONSUMPTION,

    /** At the current pace the data runs out before the next reset. */
    RUNS_OUT,

    /** At the current pace the data lasts until the next reset, so this cycle is safe. */
    SAFE,
}

enum class Confidence { LOW, MEDIUM, HIGH }

data class CycleForecast(
    val status: ForecastStatus,
    val cycle: UsageCycle,
    val remainingBytes: Long? = null,
    /** Blended pace used for the prediction. */
    val dailyBytes: Double? = null,
    val cycleAverageBytes: Double? = null,
    val recentAverageBytes: Double? = null,
    val observedDays: Double = 0.0,
    val confidence: Confidence = Confidence.LOW,
    /** Projected day the data runs out. Beyond [cycle] it is not a cycle exhaustion, see [status]. */
    val exhaustionDate: LocalDate? = null,
    val daysLeft: Int? = null,
    /** Earliest and latest exhaustion dates when the recent and whole-cycle paces disagree. */
    val earliestDate: LocalDate? = null,
    val latestDate: LocalDate? = null,
    val leftoverAtResetBytes: Long? = null,
    val dailyBudgetBytes: Double? = null,
    val measuredAt: Instant? = null,
    /** Whole days between the last reading and today. */
    val staleDays: Int = 0,
)

private const val DAY_SECONDS = 86_400.0
private const val RECENT_WINDOW_SECONDS = 7 * 86_400L

/**
 * Predicts when the data runs out using only the cycle that contains [now].
 * The pace blends the whole cycle so far with the last seven days once a week of data exists.
 */
fun forecastCycle(
    ledger: UsageLedger,
    resetDay: Int,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): CycleForecast {
    val today = now.atZone(zone).toLocalDate()
    val cycle = cycleFor(today, resetDay)
    val chain = usageChain(ledger, cycle, zone) ?: return CycleForecast(ForecastStatus.INSUFFICIENT, cycle)
    val latest = chain.readings.last()
    val remaining = latest.remainingBytes
    val cycleEndAt = cycle.endExclusive.atStartOfDay(zone).toInstant()
    val toResetDays = max(1.0, Duration.between(latest.fetchedAt, cycleEndAt).seconds / DAY_SECONDS)
    val base = CycleForecast(
        status = ForecastStatus.INSUFFICIENT,
        cycle = cycle,
        remainingBytes = remaining,
        dailyBudgetBytes = remaining / toResetDays,
        measuredAt = latest.fetchedAt,
        staleDays = Duration.between(latest.date.atStartOfDay(zone), today.atStartOfDay(zone)).toDays().toInt().coerceAtLeast(0),
    )
    if (remaining == 0L) return base.copy(status = ForecastStatus.EXHAUSTED, dailyBudgetBytes = null)

    val elapsedDays = Duration.between(chain.anchorAt, latest.fetchedAt).seconds / DAY_SECONDS
    if (elapsedDays < 1.0) return base
    val usedSoFar = max(0L, chain.anchorRemaining - remaining)
    val cycleAverage = usedSoFar / elapsedDays
    val recentReference = chain.readings.lastOrNull { it.fetchedAt <= latest.fetchedAt.minusSeconds(RECENT_WINDOW_SECONDS) }
    val recentAverage = recentReference?.let {
        max(0L, it.remainingBytes - remaining) / (Duration.between(it.fetchedAt, latest.fetchedAt).seconds / DAY_SECONDS)
    } ?: cycleAverage
    val pace = if (elapsedDays >= 7.0) 0.5 * recentAverage + 0.5 * cycleAverage else cycleAverage
    val confidence = when {
        elapsedDays < 3.0 || chain.readings.size < 2 -> Confidence.LOW
        elapsedDays < 10.0 -> Confidence.MEDIUM
        else -> Confidence.HIGH
    }
    val withPace = base.copy(
        dailyBytes = pace, cycleAverageBytes = cycleAverage, recentAverageBytes = recentAverage,
        observedDays = elapsedDays, confidence = confidence,
    )
    if (pace <= 0.0) return withPace.copy(status = ForecastStatus.NO_CONSUMPTION, dailyBytes = 0.0)

    fun exhaustionAt(rate: Double): Instant {
        val seconds = ceil(remaining / rate * DAY_SECONDS).coerceAtMost(3650 * DAY_SECONDS)
        return latest.fetchedAt.plusSeconds(seconds.toLong())
    }
    fun dateOf(instant: Instant): LocalDate = maxOf(instant.atZone(zone).toLocalDate(), today)

    val exhaustedAt = exhaustionAt(pace)
    val exhaustionDate = dateOf(exhaustedAt)
    val runsOut = exhaustedAt < cycleEndAt
    val fast = max(pace, max(cycleAverage, recentAverage))
    val slow = min(pace, min(cycleAverage, recentAverage)).takeIf { it > 0.0 } ?: pace
    val spread = fast > slow * 1.1
    return withPace.copy(
        status = if (runsOut) ForecastStatus.RUNS_OUT else ForecastStatus.SAFE,
        exhaustionDate = exhaustionDate,
        daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, exhaustionDate).toInt().coerceAtLeast(0),
        earliestDate = if (spread) dateOf(exhaustionAt(fast)) else null,
        latestDate = if (spread) dateOf(exhaustionAt(slow)) else null,
        leftoverAtResetBytes = if (runsOut) null else max(0L, (remaining - pace * toResetDays).toLong()),
    )
}
