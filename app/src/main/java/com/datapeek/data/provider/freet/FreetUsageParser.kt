package com.datapeek.data.provider.freet

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.DataUnit
import com.datapeek.data.util.ParsedDataSize
import com.datapeek.data.util.parseDataSizeCompound
import com.datapeek.data.util.parseDataSizeWithUnit
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.util.Locale

enum class UsageParseStatus {
    NOT_TESTED,
    SUCCESS,
    FAIL,
}

data class FreetUsageParseDiagnostics(
    val jsonSyntax: UsageParseStatus,
    val schemaMatched: UsageParseStatus,
    val usageFieldsFound: UsageParseStatus,
    val usageValuesValid: UsageParseStatus,
    val snapshotCreated: UsageParseStatus,
    val dataEntry: FreetUsageDataEntryDiagnostics? = null,
)

data class FreetUsageDataEntryDiagnostics(
    val total: String? = null,
    val totalText: String? = null,
    val use: String? = null,
    val useText: String? = null,
    val remain: String? = null,
    val remainText: String? = null,
    val remainRate: String? = null,
    val dataType: String? = null,
    val totalUnit: DataUnit? = null,
    val usedUnit: DataUnit? = null,
    val remainingUnit: DataUnit? = null,
)

data class FreetUsageParseResult(
    val snapshot: UsageSnapshot?,
    val diagnostics: FreetUsageParseDiagnostics,
    val error: UsageError? = null,
)

enum class UsageJsonNodeType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    INVALID,
}

data class UsageJsonNode(
    val path: String,
    val key: String?,
    val type: UsageJsonNodeType,
    val arrayLength: Int? = null,
    val isUsageCandidate: Boolean = false,
)

data class FreetUsageJsonStructure(
    val rootType: UsageJsonNodeType,
    val nodes: List<UsageJsonNode>,
    val jsonSyntax: UsageParseStatus,
    val valuesRedacted: Boolean = true,
)

/**
 * Parser for a sanitized fixture or a response body held only in memory.
 * A successful parse always contains total, used, and remaining byte values.
 */
class FreetUsageParser {
    fun parse(payload: String, fetchedAt: Instant = Instant.now()): UsageSnapshot {
        val result = parseDetailed(payload, fetchedAt)
        return result.snapshot ?: throw UsageException(
            result.error ?: UsageError.ParseError("사용량 응답을 해석하지 못했습니다."),
        )
    }

    fun parseDetailed(payload: String, fetchedAt: Instant = Instant.now()): FreetUsageParseResult {
        val trimmed = payload.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("<") || trimmed.contains("로그인", ignoreCase = true)) {
            return failure(
                jsonSyntax = UsageParseStatus.FAIL,
                schemaMatched = UsageParseStatus.FAIL,
                usageFieldsFound = UsageParseStatus.FAIL,
                usageValuesValid = UsageParseStatus.FAIL,
                error = UsageError.SessionExpired,
            )
        }

        val root = runCatching {
            val tokener = JSONTokener(trimmed)
            val parsed = tokener.nextValue()
            check(tokener.nextClean() == '\u0000')
            parsed
        }.getOrNull()
            ?: return failure(
                jsonSyntax = UsageParseStatus.FAIL,
                schemaMatched = UsageParseStatus.FAIL,
                usageFieldsFound = UsageParseStatus.FAIL,
                usageValuesValid = UsageParseStatus.FAIL,
                error = UsageError.ParseError("JSON 형식이 아닙니다."),
            )

        if (root !is JSONObject) {
            return failure(
                jsonSyntax = UsageParseStatus.SUCCESS,
                schemaMatched = UsageParseStatus.FAIL,
                usageFieldsFound = UsageParseStatus.FAIL,
                usageValuesValid = UsageParseStatus.FAIL,
                error = UsageError.ParseError("사용량 JSON schema가 아닙니다."),
            )
        }

        val syntax = UsageParseStatus.SUCCESS
        val dataEntry = root.optJSONObject(DATA_OBJECT_KEY)
            ?.optJSONArray(DATA_ARRAY_KEY)
            ?.takeIf { it.length() >= 1 }
            ?.opt(0) as? JSONObject
        val dataEntryDiagnostics = dataEntry?.let { entry ->
            FreetUsageDataEntryDiagnostics(
                total = entry.opt(TOTAL_RAW_KEY).toSafeString(),
                totalText = entry.opt(TOTAL_TEXT_KEY).toSafeString(),
                use = entry.opt(USED_RAW_KEY).toSafeString(),
                useText = entry.opt(USED_TEXT_KEY).toSafeString(),
                remain = entry.opt(REMAINING_RAW_KEY).toSafeString(),
                remainText = entry.opt(REMAINING_TEXT_KEY).toSafeString(),
                remainRate = entry.opt(REMAINING_RATE_KEY).toSafeString(),
                dataType = entry.opt(DATA_TYPE_KEY).toSafeString(),
            )
        }
        val fieldPresence = dataEntry?.let { entry ->
            CORE_FIELDS.map { (_, textKey, rawKey) -> entry.has(textKey) || entry.has(rawKey) }
        }.orEmpty()
        val allFieldsFound = dataEntry != null && fieldPresence.all { it }
        val schema = allFieldsFound.toParseStatus()
        val fields = allFieldsFound.toParseStatus()
        if (!allFieldsFound) {
            val message = if (dataEntry == null || fieldPresence.none { it }) {
                "사용량 필드가 없습니다."
            } else {
                "사용량 필드가 부족합니다."
            }
            return failure(
                jsonSyntax = syntax,
                schemaMatched = schema,
                usageFieldsFound = fields,
                usageValuesValid = UsageParseStatus.FAIL,
                error = UsageError.ParseError(message),
                dataEntry = dataEntryDiagnostics,
            )
        }

        val total = parseDataField(dataEntry, TOTAL_TEXT_KEY, TOTAL_RAW_KEY)
        val used = parseDataField(dataEntry, USED_TEXT_KEY, USED_RAW_KEY)
        val remaining = parseDataField(dataEntry, REMAINING_TEXT_KEY, REMAINING_RAW_KEY)
        val parsedDataDiagnostics = dataEntryDiagnostics?.copy(
            totalUnit = total?.unit,
            usedUnit = used?.unit,
            remainingUnit = remaining?.unit,
        )
        val valuesValid = total != null && used != null && remaining != null &&
            total.bytes > 0L && used.bytes <= total.bytes && remaining.bytes <= total.bytes
        if (!valuesValid) {
            return failure(
                jsonSyntax = syntax,
                schemaMatched = schema,
                usageFieldsFound = fields,
                usageValuesValid = UsageParseStatus.FAIL,
                error = UsageError.ParseError("사용량 숫자를 해석하지 못했습니다."),
                dataEntry = parsedDataDiagnostics,
            )
        }

        val remainingPercent = (remaining.bytes.toDouble() / total.bytes * 100.0)
            .toFloat()
            .coerceIn(0f, 100f)
        val snapshot = UsageSnapshot(
            carrier = Carrier.FREET_SKT,
            totalBytes = total.bytes,
            usedBytes = used.bytes,
            remainingBytes = remaining.bytes,
            // Freet's raw values are KiB and its text values may mix units.
            // Leave display selection adaptive so 308M 341K is not rendered
            // as a forced 0.3 GB (or as a forced MB) after parsing.
            totalUnit = null,
            usedUnit = null,
            remainingUnit = null,
            remainingPercent = remainingPercent,
            fetchedAt = fetchedAt,
            source = UsageSource.CARRIER_SERVER,
        )
        return FreetUsageParseResult(
            snapshot = snapshot,
            diagnostics = FreetUsageParseDiagnostics(
                jsonSyntax = syntax,
                schemaMatched = schema,
                usageFieldsFound = fields,
                usageValuesValid = UsageParseStatus.SUCCESS,
                snapshotCreated = UsageParseStatus.SUCCESS,
                dataEntry = parsedDataDiagnostics,
            ),
        )
    }

    private fun parseDataField(
        entry: JSONObject,
        textKey: String,
        rawKey: String,
    ): ParsedDataSize? {
        // The numeric DATA fields are the exact server values and are always
        // expressed in KiB. Text is only a fallback for malformed/missing raw
        // values and must carry its own explicit unit(s).
        return parseRawKib(entry.opt(rawKey))
            ?: entry.opt(textKey).toSafeString()?.let { parseDataSizeCompound(it) }
    }

    private fun parseRawKib(value: Any?): ParsedDataSize? {
        val raw = value.toSafeString()?.trim() ?: return null
        if (!RAW_KIB_PATTERN.matches(raw)) return null
        return parseDataSizeWithUnit(raw, DataUnit.KIB)
    }

    private fun Any?.toSafeString(): String? = when (this) {
        null,
        JSONObject.NULL,
        -> null
        is String,
        is Number,
        -> toString()
        else -> null
    }

    private fun failure(
        jsonSyntax: UsageParseStatus,
        schemaMatched: UsageParseStatus,
        usageFieldsFound: UsageParseStatus,
        usageValuesValid: UsageParseStatus,
        error: UsageError,
        dataEntry: FreetUsageDataEntryDiagnostics? = null,
    ) = FreetUsageParseResult(
        snapshot = null,
        diagnostics = FreetUsageParseDiagnostics(
            jsonSyntax = jsonSyntax,
            schemaMatched = schemaMatched,
            usageFieldsFound = usageFieldsFound,
            usageValuesValid = usageValuesValid,
            snapshotCreated = UsageParseStatus.FAIL,
            dataEntry = dataEntry,
        ),
        error = error,
    )

    companion object {
        private const val DATA_OBJECT_KEY = "data"
        private const val DATA_ARRAY_KEY = "DATA"
        private const val TOTAL_TEXT_KEY = "totalText"
        private const val TOTAL_RAW_KEY = "total"
        private const val USED_TEXT_KEY = "useText"
        private const val USED_RAW_KEY = "use"
        private const val REMAINING_TEXT_KEY = "remainText"
        private const val REMAINING_RAW_KEY = "remain"
        private const val REMAINING_RATE_KEY = "remainRate"
        private const val DATA_TYPE_KEY = "dataType"
        private val CORE_FIELDS = listOf(
            Triple("total", TOTAL_TEXT_KEY, TOTAL_RAW_KEY),
            Triple("use", USED_TEXT_KEY, USED_RAW_KEY),
            Triple("remain", REMAINING_TEXT_KEY, REMAINING_RAW_KEY),
        )
        private val USAGE_SCHEMA_KEYS = setOf(
            "total",
            "totaltext",
            "use",
            "usetext",
            "remain",
            "remaintext",
            "remainrate",
        )
        private val RAW_KIB_PATTERN = Regex("\\d+")

        internal fun isUsageCandidateKey(key: String): Boolean {
            val normalized = key.normalizeKey()
            return normalized in USAGE_SCHEMA_KEYS
        }
    }
}

internal fun inspectFreetUsageJsonStructure(payload: String): FreetUsageJsonStructure {
    val trimmed = payload.trim().removePrefix("\uFEFF")
    if (trimmed.isEmpty()) return invalidJsonStructure()

    val root = runCatching {
        val tokener = JSONTokener(trimmed)
        val parsed = tokener.nextValue()
        check(tokener.nextClean() == '\u0000')
        parsed
    }.getOrNull() ?: return invalidJsonStructure()

    // JSONTokener accepts an unquoted bare word as a String. That is not a
    // JSON response body and must not be presented as a valid structure.
    if (root is String && !trimmed.startsWith('"')) return invalidJsonStructure()

    val nodes = mutableListOf<UsageJsonNode>()
    appendJsonNode(root, "\$", null, 0, nodes)
    return FreetUsageJsonStructure(
        rootType = jsonNodeType(root),
        nodes = nodes,
        jsonSyntax = UsageParseStatus.SUCCESS,
    )
}

private fun invalidJsonStructure() = FreetUsageJsonStructure(
    rootType = UsageJsonNodeType.INVALID,
    nodes = emptyList(),
    jsonSyntax = UsageParseStatus.FAIL,
)

private fun appendJsonNode(
    value: Any?,
    path: String,
    key: String?,
    depth: Int,
    nodes: MutableList<UsageJsonNode>,
) {
    if (nodes.size >= MAX_STRUCTURE_NODES) return
    val type = jsonNodeType(value)
    nodes += UsageJsonNode(
        path = path,
        key = key?.let(::safeStructureKey),
        type = type,
        arrayLength = (value as? JSONArray)?.length(),
        isUsageCandidate = key?.let(FreetUsageParser::isUsageCandidateKey) == true,
    )
    if (depth >= MAX_STRUCTURE_DEPTH) return
    when (value) {
        is JSONObject -> {
            val iterator = value.keys()
            while (iterator.hasNext() && nodes.size < MAX_STRUCTURE_NODES) {
                val childKey = iterator.next()
                appendJsonNode(
                    value = value.opt(childKey),
                    path = "$path.${safeStructureKey(childKey)}",
                    key = childKey,
                    depth = depth + 1,
                    nodes = nodes,
                )
            }
        }
        is JSONArray -> {
            for (index in 0 until value.length()) {
                if (nodes.size >= MAX_STRUCTURE_NODES) break
                appendJsonNode(value.opt(index), "$path[$index]", null, depth + 1, nodes)
            }
        }
    }
}

private fun jsonNodeType(value: Any?): UsageJsonNodeType = when (value) {
    is JSONObject -> UsageJsonNodeType.OBJECT
    is JSONArray -> UsageJsonNodeType.ARRAY
    is String -> UsageJsonNodeType.STRING
    is Number -> UsageJsonNodeType.NUMBER
    is Boolean -> UsageJsonNodeType.BOOLEAN
    null,
    JSONObject.NULL,
    -> UsageJsonNodeType.NULL
    else -> UsageJsonNodeType.INVALID
}

private fun safeStructureKey(key: String): String {
    val normalized = key.lowercase(Locale.US).replace("_", "").replace("-", "")
    return if (SENSITIVE_KEY_PARTS.any { normalized.contains(it) }) "<redacted-key>" else key
}

private fun String.normalizeKey() = lowercase(Locale.US).replace("_", "").replace("-", "")

private fun Boolean.toParseStatus() = if (this) UsageParseStatus.SUCCESS else UsageParseStatus.FAIL

private const val MAX_STRUCTURE_DEPTH = 4
private const val MAX_STRUCTURE_NODES = 120
private val SENSITIVE_KEY_PARTS = setOf(
    "token",
    "cookie",
    "session",
    "phone",
    "msisdn",
    "email",
    "address",
    "name",
    "customer",
    "member",
    "birth",
    "resident",
    "username",
    "userid",
    "lineid",
)
