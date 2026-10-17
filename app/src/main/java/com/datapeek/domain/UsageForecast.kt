package com.datapeek.domain

import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

data class UsageObservation(
    val scope: String,
    val totalBytes: Long?,
    val remainingBytes: Long,
    val fetchedAt: Instant,
    val cycleStart: LocalDate?,
    val resetDate: LocalDate?,
)

enum class ForecastStatus { LEARNING, PREDICTED, NO_CONSUMPTION, LOW_CONSUMPTION, EXHAUSTED, STALE }

data class UsageForecast(
    val status: ForecastStatus,
    val dailyBytes: Double? = null,
    val exhaustedAt: Instant? = null,
    val runsOutBeforeReset: Boolean? = null,
    val dailyBudgetBytes: Double? = null,
    val observedDays: Double = 0.0,
    val measuredAt: Instant? = null,
)

private const val DAY_SECONDS = 86_400L
private const val WINDOW_SECONDS = 7 * DAY_SECONDS

fun recordUsage(
    history: List<UsageObservation>,
    snapshot: UsageSnapshot,
    zone: ZoneId = ZoneId.systemDefault(),
): List<UsageObservation> {
    if (snapshot.source !in setOf(UsageSource.CARRIER_SERVER, UsageSource.WEB_PAGE)) return history
    val remaining = snapshot.remainingBytes?.takeIf { it >= 0 } ?: return history
    if (snapshot.totalBytes != null && (snapshot.totalBytes < 0 || remaining > snapshot.totalBytes)) return history
    val identity = listOf(snapshot.carrier.name, snapshot.lineId, snapshot.phoneNumberMasked, snapshot.lineName)
        .joinToString("\u0000") { it.orEmpty() }
    val scope = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val observation = UsageObservation(
        scope = scope,
        totalBytes = snapshot.totalBytes,
        remainingBytes = remaining,
        fetchedAt = snapshot.fetchedAt,
        cycleStart = snapshot.cycleStart,
        resetDate = snapshot.cycleEnd ?: snapshot.daysUntilReset?.takeIf { it in 0..366 }?.let {
            snapshot.fetchedAt.atZone(zone).toLocalDate().plusDays(it.toLong())
        },
    )
    val previous = history.lastOrNull() ?: return listOf(observation)
    if (observation.fetchedAt <= previous.fetchedAt) return history
    val newSegment = observation.scope != previous.scope || observation.totalBytes != previous.totalBytes ||
        observation.cycleStart != previous.cycleStart || observation.resetDate != previous.resetDate ||
        observation.remainingBytes > previous.remainingBytes ||
        Duration.between(previous.fetchedAt, observation.fetchedAt).seconds > WINDOW_SECONDS
    if (newSegment) return listOf(observation)
    val retained = history.filter { it.fetchedAt >= observation.fetchedAt.minusSeconds(WINDOW_SECONDS) }
    // Keep an hourly baseline and the latest reading, even during repeated manual refreshes.
    val compact = if (retained.size >= 2 &&
        Duration.between(retained[retained.lastIndex - 1].fetchedAt, observation.fetchedAt).seconds < 3_600
    ) retained.dropLast(1) else retained
    return (compact + observation).takeLast(200)
}

fun forecastUsage(
    history: List<UsageObservation>,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): UsageForecast {
    val latest = history.lastOrNull() ?: return UsageForecast(ForecastStatus.LEARNING)
    val age = Duration.between(latest.fetchedAt, now).seconds
    val resetAt = latest.resetDate?.atStartOfDay(zone)?.toInstant()
    if (age < 0 || age > DAY_SECONDS || (resetAt != null && now >= resetAt)) {
        return UsageForecast(ForecastStatus.STALE, measuredAt = latest.fetchedAt)
    }
    val secondsToReset = resetAt?.let { Duration.between(latest.fetchedAt, it).seconds }
    val budget = secondsToReset?.takeIf { it > 0 }?.let { latest.remainingBytes.toDouble() * DAY_SECONDS / it }
    val baseline = UsageForecast(
        status = ForecastStatus.LEARNING,
        dailyBudgetBytes = budget,
        measuredAt = latest.fetchedAt,
    )
    if (latest.remainingBytes == 0L) return baseline.copy(status = ForecastStatus.EXHAUSTED)
    val first = history.firstOrNull {
        it.fetchedAt >= latest.fetchedAt.minusSeconds(WINDOW_SECONDS)
    } ?: return baseline
    val elapsed = Duration.between(first.fetchedAt, latest.fetchedAt).seconds
    if (elapsed < DAY_SECONDS) return baseline
    val observedDays = elapsed.toDouble() / DAY_SECONDS
    val consumed = first.remainingBytes - latest.remainingBytes
    if (consumed <= 0L) return baseline.copy(
        status = ForecastStatus.NO_CONSUMPTION, dailyBytes = 0.0, observedDays = observedDays,
    )
    val daily = consumed / observedDays
    val secondsLeft = ceil(latest.remainingBytes / daily * DAY_SECONDS)
    if (!secondsLeft.isFinite() || secondsLeft > 36600.0 * DAY_SECONDS) {
        return baseline.copy(status = ForecastStatus.LOW_CONSUMPTION, dailyBytes = daily, observedDays = observedDays)
    }
    val exhaustedAt = latest.fetchedAt.plusSeconds(secondsLeft.toLong())
    return baseline.copy(
        status = ForecastStatus.PREDICTED,
        dailyBytes = daily,
        exhaustedAt = exhaustedAt,
        runsOutBeforeReset = resetAt?.let { exhaustedAt < it },
        observedDays = observedDays,
    )
}
