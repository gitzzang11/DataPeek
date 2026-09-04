package com.datapeek.data.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val BYTES_PER_KIB = 1024L
private const val BYTES_PER_MIB = BYTES_PER_KIB * 1024L
private const val BYTES_PER_GIB = BYTES_PER_MIB * 1024L

enum class DataUnit(val bytesMultiplier: BigDecimal, val displayLabel: String) {
    BYTES(BigDecimal.ONE, "B"),
    KIB(BigDecimal.valueOf(BYTES_PER_KIB), "KB"),
    MIB(BigDecimal.valueOf(BYTES_PER_MIB), "MB"),
    GIB(BigDecimal.valueOf(BYTES_PER_GIB), "GB"),
}

fun formatBytes(bytes: Long?, preferredUnit: DataUnit? = null): String {
    val safeBytes = bytes?.takeIf { it >= 0 } ?: return "—"
    val unit = preferredUnit ?: if (safeBytes < BYTES_PER_GIB) DataUnit.MIB else DataUnit.GIB
    return "${formatDecimal(safeBytes / unit.bytesMultiplier.toDouble())} ${unit.displayLabel}"
}

fun calculateRemainingPercent(remainingBytes: Long?, totalBytes: Long?): Float? {
    if (remainingBytes == null || totalBytes == null || totalBytes <= 0L) return null
    return (remainingBytes.toDouble() / totalBytes * 100.0).toFloat().coerceIn(0f, 100f)
}

fun daysUntilReset(end: LocalDate?, today: LocalDate = LocalDate.now()): Int? =
    end?.let { ChronoUnit.DAYS.between(today, it).toInt().coerceAtLeast(0) }

fun resetLabel(days: Int?): String = when (days) {
    null -> "초기화일 정보 없음"
    0 -> "오늘 초기화"
    1 -> "내일 초기화"
    else -> "${days}일 후 초기화"
}

fun parseDataSize(value: String, fallbackUnit: DataUnit? = null): Long? =
    parseDataSizeWithUnit(value, fallbackUnit)?.bytes

/**
 * Parses a display value made of one or more explicitly unit-qualified parts.
 * For example, Freet uses values such as "308M 341K".
 */
fun parseDataSizeCompound(value: String): ParsedDataSize? {
    val raw = value.trim()
    if (raw.isEmpty()) return null

    val parts = COMPOUND_DATA_SIZE_PATTERN.findAll(raw).toList()
    if (parts.isEmpty()) return null

    var previousEnd = 0
    var totalBytes = BigDecimal.ZERO
    var largestUnit: DataUnit? = null
    for (part in parts) {
        if (raw.substring(previousEnd, part.range.first).isNotBlank()) return null
        val parsed = parseDataSizeWithUnit(part.value) ?: return null
        totalBytes = totalBytes.add(BigDecimal.valueOf(parsed.bytes))
        if (largestUnit == null || parsed.unit.bytesMultiplier > largestUnit.bytesMultiplier) {
            largestUnit = parsed.unit
        }
        previousEnd = part.range.last + 1
    }
    if (raw.substring(previousEnd).isNotBlank()) return null

    val bytes = totalBytes.takeIf { it <= BigDecimal.valueOf(Long.MAX_VALUE) }?.toLong() ?: return null
    return ParsedDataSize(bytes = bytes, unit = largestUnit ?: return null)
}

data class ParsedDataSize(
    val bytes: Long,
    val unit: DataUnit,
)

fun parseDataSizeWithUnit(value: String, fallbackUnit: DataUnit? = null): ParsedDataSize? {
    val raw = value.replace(",", "").trim()
    val match = DATA_SIZE_PATTERN.matchEntire(raw) ?: return null
    val number = runCatching { BigDecimal(match.groupValues[1]) }.getOrNull() ?: return null
    if (number.signum() < 0) return null
    val explicitUnit = parseDataUnit(match.groupValues[2])
    val unit = explicitUnit ?: fallbackUnit ?: return null
    val bytes = number.multiply(unit.bytesMultiplier).setScale(0, RoundingMode.HALF_UP)
    return bytes.takeIf { it <= BigDecimal.valueOf(Long.MAX_VALUE) }?.toLong()?.let {
        ParsedDataSize(bytes = it, unit = unit)
    }
}

fun parseDataUnit(value: String?): DataUnit? = when (value?.trim()?.uppercase(Locale.US)) {
    "B", "BYTE", "BYTES" -> DataUnit.BYTES
    "K", "KB", "KIB" -> DataUnit.KIB
    "M", "MB", "MIB" -> DataUnit.MIB
    "G", "GB", "GIB" -> DataUnit.GIB
    else -> null
}

private val DATA_SIZE_PATTERN = Regex(
    "^(-?\\d+(?:\\.\\d+)?)\\s*(bytes?|gib|gb|g|mib|mb|m|kib|kb|k|b)?$",
    RegexOption.IGNORE_CASE,
)

private val COMPOUND_DATA_SIZE_PATTERN = Regex(
    "-?\\d+(?:\\.\\d+)?\\s*(?:bytes?|gib|gb|g|mib|mb|m|kib|kb|k|b)",
    RegexOption.IGNORE_CASE,
)

private fun formatDecimal(value: Double): String {
    if (!value.isFinite() || value < 0) return "—"
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
