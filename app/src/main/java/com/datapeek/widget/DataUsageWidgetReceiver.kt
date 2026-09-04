package com.datapeek.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class DataUsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DataUsageWidget()

    override fun onDeleted(context: android.content.Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            WidgetConfigStore.delete(context, it)
            WidgetSizeDiagnosticsStore.delete(context, it)
        }
        super.onDeleted(context, appWidgetIds)
    }
}
