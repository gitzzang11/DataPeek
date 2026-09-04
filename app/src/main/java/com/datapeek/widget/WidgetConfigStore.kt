package com.datapeek.widget

import android.content.Context
import androidx.core.content.edit

private const val WIDGET_PREFERENCES = "data_usage_widget_config"
private const val STYLE_FIELD = "style"
private const val THEME_FIELD = "theme"
private const val BACKGROUND_FIELD = "background_enabled"
private const val OPACITY_FIELD = "opacity_percent"
private const val SHOW_REFRESH_FIELD = "show_refresh"
private const val SHOW_UPDATED_FIELD = "show_updated"

internal fun widgetPreferenceKey(appWidgetId: Int, field: String): String =
    "widget_${appWidgetId}_$field"

internal data class StoredWidgetConfig(
    val style: String,
    val theme: String,
    val backgroundEnabled: Boolean,
    val opacityPercent: Int,
    val showRefresh: Boolean,
    val showUpdated: Boolean,
)

internal fun encodeWidgetConfig(config: WidgetConfig): StoredWidgetConfig = StoredWidgetConfig(
    style = config.style.name,
    theme = config.theme.name,
    backgroundEnabled = config.backgroundEnabled,
    opacityPercent = config.safeOpacityPercent,
    showRefresh = config.showRefresh,
    showUpdated = config.showUpdated,
)

internal fun decodeWidgetConfig(stored: StoredWidgetConfig): WidgetConfig = WidgetConfig(
    style = runCatching { WidgetStyle.valueOf(stored.style) }.getOrNull() ?: WidgetStyle.INFO,
    theme = parseStoredWidgetTheme(stored.theme),
    backgroundEnabled = stored.backgroundEnabled,
    opacityPercent = stored.opacityPercent.coerceIn(0, 100),
    showRefresh = stored.showRefresh,
    showUpdated = stored.showUpdated,
)

internal object WidgetConfigStore {
    fun read(context: Context, appWidgetId: Int): WidgetConfig {
        val preferences = context.applicationContext.getSharedPreferences(
            WIDGET_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        return decodeWidgetConfig(
            StoredWidgetConfig(
                style = preferences.getString(widgetPreferenceKey(appWidgetId, STYLE_FIELD), null)
                    ?: WidgetStyle.INFO.name,
                theme = preferences.getString(widgetPreferenceKey(appWidgetId, THEME_FIELD), null)
                    ?: WidgetTheme.SYSTEM.name,
                backgroundEnabled = preferences.getBoolean(
                    widgetPreferenceKey(appWidgetId, BACKGROUND_FIELD),
                    true,
                ),
                opacityPercent = preferences.getInt(
                    widgetPreferenceKey(appWidgetId, OPACITY_FIELD),
                    0,
                ).coerceIn(0, 100),
                showRefresh = preferences.getBoolean(
                    widgetPreferenceKey(appWidgetId, SHOW_REFRESH_FIELD),
                    true,
                ),
                showUpdated = preferences.getBoolean(
                    widgetPreferenceKey(appWidgetId, SHOW_UPDATED_FIELD),
                    true,
                ),
            ),
        )
    }

    fun save(context: Context, appWidgetId: Int, config: WidgetConfig) {
        val stored = encodeWidgetConfig(config)
        context.applicationContext.getSharedPreferences(
            WIDGET_PREFERENCES,
            Context.MODE_PRIVATE,
        ).edit {
            putString(widgetPreferenceKey(appWidgetId, STYLE_FIELD), stored.style)
            putString(widgetPreferenceKey(appWidgetId, THEME_FIELD), stored.theme)
            putBoolean(widgetPreferenceKey(appWidgetId, BACKGROUND_FIELD), stored.backgroundEnabled)
            putInt(widgetPreferenceKey(appWidgetId, OPACITY_FIELD), stored.opacityPercent)
            putBoolean(widgetPreferenceKey(appWidgetId, SHOW_REFRESH_FIELD), stored.showRefresh)
            putBoolean(widgetPreferenceKey(appWidgetId, SHOW_UPDATED_FIELD), stored.showUpdated)
        }
    }

    fun delete(context: Context, appWidgetId: Int) {
        context.applicationContext.getSharedPreferences(
            WIDGET_PREFERENCES,
            Context.MODE_PRIVATE,
        ).edit {
            remove(widgetPreferenceKey(appWidgetId, STYLE_FIELD))
            remove(widgetPreferenceKey(appWidgetId, THEME_FIELD))
            remove(widgetPreferenceKey(appWidgetId, BACKGROUND_FIELD))
            remove(widgetPreferenceKey(appWidgetId, OPACITY_FIELD))
            remove(widgetPreferenceKey(appWidgetId, SHOW_REFRESH_FIELD))
            remove(widgetPreferenceKey(appWidgetId, SHOW_UPDATED_FIELD))
        }
    }
}

internal fun parseStoredWidgetTheme(value: String): WidgetTheme = when (value) {
    WidgetTheme.SYSTEM.name -> WidgetTheme.SYSTEM
    WidgetTheme.LIGHT_FOREGROUND.name, "DARK" -> WidgetTheme.LIGHT_FOREGROUND
    WidgetTheme.DARK_FOREGROUND.name, "LIGHT" -> WidgetTheme.DARK_FOREGROUND
    else -> WidgetTheme.SYSTEM
}
