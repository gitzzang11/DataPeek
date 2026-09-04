package com.datapeek.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.DataUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

private val Context.usageDataStore by preferencesDataStore(name = "usage_cache")

object UsageCacheStore {
    private val carrierKey = stringPreferencesKey("carrier")
    private val lineNameKey = stringPreferencesKey("line_name")
    private val totalKey = longPreferencesKey("total_bytes")
    private val usedKey = longPreferencesKey("used_bytes")
    private val remainingKey = longPreferencesKey("remaining_bytes")
    private val totalUnitKey = stringPreferencesKey("total_unit")
    private val usedUnitKey = stringPreferencesKey("used_unit")
    private val remainingUnitKey = stringPreferencesKey("remaining_unit")
    private val percentKey = floatPreferencesKey("remaining_percent")
    private val cycleStartKey = stringPreferencesKey("cycle_start")
    private val cycleEndKey = stringPreferencesKey("cycle_end")
    private val fetchedAtKey = stringPreferencesKey("fetched_at")
    private val sourceKey = stringPreferencesKey("source")
    private val errorKey = stringPreferencesKey("last_error")
    private val refreshingKey = booleanPreferencesKey("refreshing")
    private val developerModeEnabledKey = booleanPreferencesKey("developer_mode_enabled")
    private val autoRefreshEnabledKey = booleanPreferencesKey("auto_refresh_enabled")
    private val refreshDueKey = booleanPreferencesKey("refresh_due")
    private val lastBackgroundAttemptKey = stringPreferencesKey("last_background_attempt")
    private val lastBackgroundResultKey = stringPreferencesKey("last_background_result")
    private val lastRefreshTriggerKey = stringPreferencesKey("last_refresh_trigger")

    suspend fun readSnapshot(context: Context): UsageSnapshot? = context.usageDataStore.data.first().toSnapshot()

    fun snapshotFlow(context: Context): Flow<UsageSnapshot?> = context.usageDataStore.data.map { it.toSnapshot() }

    suspend fun saveSnapshot(context: Context, snapshot: UsageSnapshot) {
        context.usageDataStore.edit { preferences ->
            preferences[carrierKey] = snapshot.carrier.name
            snapshot.lineName?.let { preferences[lineNameKey] = it } ?: preferences.remove(lineNameKey)
            snapshot.totalBytes?.let { preferences[totalKey] = it } ?: preferences.remove(totalKey)
            snapshot.usedBytes?.let { preferences[usedKey] = it } ?: preferences.remove(usedKey)
            snapshot.remainingBytes?.let { preferences[remainingKey] = it } ?: preferences.remove(remainingKey)
            snapshot.totalUnit?.let { preferences[totalUnitKey] = it.name } ?: preferences.remove(totalUnitKey)
            snapshot.usedUnit?.let { preferences[usedUnitKey] = it.name } ?: preferences.remove(usedUnitKey)
            snapshot.remainingUnit?.let { preferences[remainingUnitKey] = it.name } ?: preferences.remove(remainingUnitKey)
            snapshot.remainingPercent?.let { preferences[percentKey] = it } ?: preferences.remove(percentKey)
            snapshot.cycleStart?.let { preferences[cycleStartKey] = it.toString() } ?: preferences.remove(cycleStartKey)
            snapshot.cycleEnd?.let { preferences[cycleEndKey] = it.toString() } ?: preferences.remove(cycleEndKey)
            preferences[fetchedAtKey] = snapshot.fetchedAt.toString()
            preferences[sourceKey] = snapshot.source.name
            preferences.remove(errorKey)
            preferences[refreshDueKey] = false
        }
    }

    suspend fun saveError(context: Context, message: String) {
        context.usageDataStore.edit { it[errorKey] = message }
    }

    suspend fun readError(context: Context): String? = context.usageDataStore.data.first()[errorKey]

    fun errorFlow(context: Context): Flow<String?> = context.usageDataStore.data.map { it[errorKey] }

    fun developerModeEnabledFlow(context: Context): Flow<Boolean> =
        context.usageDataStore.data.map { it[developerModeEnabledKey] ?: false }

    fun autoRefreshEnabledFlow(context: Context): Flow<Boolean> =
        context.usageDataStore.data.map { it.autoRefreshEnabled() }

    fun refreshDueFlow(context: Context): Flow<Boolean> =
        context.usageDataStore.data.map { it[refreshDueKey] ?: false }

    fun lastBackgroundAttemptFlow(context: Context): Flow<Instant?> =
        context.usageDataStore.data.map { preferences ->
            preferences[lastBackgroundAttemptKey]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        }

    fun lastBackgroundResultFlow(context: Context): Flow<String?> =
        context.usageDataStore.data.map { it[lastBackgroundResultKey] }

    fun lastRefreshTriggerFlow(context: Context): Flow<String?> =
        context.usageDataStore.data.map { it[lastRefreshTriggerKey] }

    suspend fun readDeveloperModeEnabled(context: Context): Boolean =
        context.usageDataStore.data.first()[developerModeEnabledKey] ?: false

    suspend fun setDeveloperModeEnabled(context: Context, enabled: Boolean) {
        context.usageDataStore.edit { preferences ->
            preferences[developerModeEnabledKey] = enabled
        }
    }

    suspend fun readAutoRefreshEnabled(context: Context): Boolean =
        context.usageDataStore.data.first().autoRefreshEnabled()

    suspend fun setAutoRefreshEnabled(context: Context, enabled: Boolean) {
        context.usageDataStore.edit { preferences ->
            preferences[autoRefreshEnabledKey] = enabled
        }
    }

    suspend fun clear(context: Context) {
        context.usageDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearSnapshot(context: Context) {
        context.usageDataStore.edit { preferences ->
            preferences.remove(carrierKey)
            preferences.remove(lineNameKey)
            preferences.remove(totalKey)
            preferences.remove(usedKey)
            preferences.remove(remainingKey)
            preferences.remove(totalUnitKey)
            preferences.remove(usedUnitKey)
            preferences.remove(remainingUnitKey)
            preferences.remove(percentKey)
            preferences.remove(cycleStartKey)
            preferences.remove(cycleEndKey)
            preferences.remove(fetchedAtKey)
            preferences.remove(sourceKey)
            preferences.remove(errorKey)
            preferences.remove(refreshingKey)
            preferences.remove(refreshDueKey)
        }
    }

    suspend fun setRefreshing(context: Context, refreshing: Boolean) {
        context.usageDataStore.edit { preferences ->
            preferences[refreshingKey] = refreshing
        }
    }

    suspend fun readRefreshing(context: Context): Boolean =
        context.usageDataStore.data.first()[refreshingKey] ?: false

    suspend fun markBackgroundRefreshDue(
        context: Context,
        attemptedAt: Instant = Instant.now(),
        result: String = BACKGROUND_DEFERRED_RESULT,
    ) {
        context.usageDataStore.edit { preferences ->
            preferences[refreshDueKey] = true
            preferences[lastBackgroundAttemptKey] = attemptedAt.toString()
            preferences[lastBackgroundResultKey] = result
            preferences[lastRefreshTriggerKey] = "BACKGROUND"
        }
    }

    suspend fun recordRefreshTrigger(context: Context, trigger: String) {
        context.usageDataStore.edit { preferences ->
            preferences[lastRefreshTriggerKey] = trigger
        }
    }

    suspend fun clearRefreshDue(context: Context) {
        context.usageDataStore.edit { preferences ->
            preferences[refreshDueKey] = false
        }
    }

    suspend fun readRefreshDue(context: Context): Boolean =
        context.usageDataStore.data.first()[refreshDueKey] ?: false

    suspend fun setAutoRefreshMinutes(context: Context, minutes: Long) {
        context.usageDataStore.edit { it[autoRefreshKey] = minutes.coerceAtLeast(0L) }
    }

    suspend fun readAutoRefreshMinutes(context: Context): Long =
        context.usageDataStore.data.first()[autoRefreshKey] ?: DEFAULT_REFRESH_MINUTES

    private fun Preferences.autoRefreshEnabled(): Boolean =
        get(autoRefreshEnabledKey) ?: (get(autoRefreshKey)?.let { it > 0L } ?: DEFAULT_AUTO_REFRESH_ENABLED)

    private fun Preferences.toSnapshot(): UsageSnapshot? {
        val carrier = get(carrierKey)?.let { runCatching { Carrier.valueOf(it) }.getOrNull() } ?: return null
        val fetchedAt = get(fetchedAtKey)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return UsageSnapshot(
            carrier = carrier,
            lineName = get(lineNameKey),
            totalBytes = get(totalKey),
            usedBytes = get(usedKey),
            remainingBytes = get(remainingKey),
            totalUnit = get(totalUnitKey)?.let { runCatching { DataUnit.valueOf(it) }.getOrNull() },
            usedUnit = get(usedUnitKey)?.let { runCatching { DataUnit.valueOf(it) }.getOrNull() },
            remainingUnit = get(remainingUnitKey)?.let { runCatching { DataUnit.valueOf(it) }.getOrNull() },
            remainingPercent = get(percentKey),
            cycleStart = get(cycleStartKey)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            cycleEnd = get(cycleEndKey)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            fetchedAt = fetchedAt,
            source = get(sourceKey)?.let { runCatching { UsageSource.valueOf(it) }.getOrNull() }
                ?: UsageSource.CARRIER_SERVER,
        )
    }

    private val autoRefreshKey = longPreferencesKey("auto_refresh_minutes")
    private const val DEFAULT_REFRESH_MINUTES = 60L
    private const val DEFAULT_AUTO_REFRESH_ENABLED = true
    const val BACKGROUND_DEFERRED_RESULT = "DEFERRED_TO_FOREGROUND"
}
