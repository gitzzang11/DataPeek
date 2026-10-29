package com.datapeek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datapeek.data.util.formatBytes
import com.datapeek.domain.CycleReport
import java.time.LocalDate
import kotlin.math.max

internal enum class ChartMode { DAILY, CUMULATIVE }

/** One day of a cycle: [bytes] is null when the day has no usable usage figure yet (future, first reading, top-up). */
internal data class ChartDay(
    val date: LocalDate,
    val bytes: Double?,
    val estimated: Boolean,
    val cumulativeBytes: Double?,
)

/** Spreads the report's spans over every calendar day of the cycle. */
internal fun chartDays(report: CycleReport): List<ChartDay> {
    val bytes = HashMap<LocalDate, Pair<Double?, Boolean>>()
    report.spans.forEach { span ->
        var date = span.start
        while (date <= span.end) {
            bytes[date] = span.perDayBytes to span.isEstimate
            date = date.plusDays(1)
        }
    }
    val lastRecorded = report.spans.lastOrNull()?.end
    var running = 0.0
    return generateSequence(report.cycle.start) { it.plusDays(1) }.takeWhile { it < report.cycle.endExclusive }.map { date ->
        val entry = bytes[date]
        if (entry?.first != null) running += entry.first!!
        ChartDay(
            date = date,
            bytes = entry?.first,
            estimated = entry?.second ?: false,
            cumulativeBytes = if (lastRecorded != null && date <= lastRecorded) running else null,
        )
    }.toList()
}

/** Dashed forecast: the cumulative line continues at [dailyBytes] per day from the last recorded day. */
@Composable
internal fun UsageChart(
    days: List<ChartDay>,
    mode: ChartMode,
    today: LocalDate,
    description: String,
    dailyPaceBytes: Double?,
    limitBytes: Long?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant, fontSize = 11.sp)
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    Canvas(modifier = modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
        if (days.isEmpty()) return@Canvas
        val top = 22.dp.toPx()
        val bottom = 22.dp.toPx()
        val plotHeight = size.height - top - bottom
        val slot = size.width / days.size
        val lastIndex = days.indexOfLast { it.cumulativeBytes != null }
        val lastCumulative = if (lastIndex >= 0) days[lastIndex].cumulativeBytes ?: 0.0 else 0.0
        val projectedEnd = dailyPaceBytes?.takeIf { mode == ChartMode.CUMULATIVE && lastIndex >= 0 }
            ?.let { lastCumulative + it * (days.size - 1 - lastIndex) }
        val maxValue = when (mode) {
            ChartMode.DAILY -> max(days.maxOf { it.bytes ?: 0.0 }, dailyPaceBytes ?: 0.0)
            ChartMode.CUMULATIVE -> max(max(lastCumulative, projectedEnd ?: 0.0), limitBytes?.toDouble() ?: 0.0)
        }.coerceAtLeast(1.0)
        fun y(value: Double) = top + plotHeight * (1f - (value / maxValue).toFloat().coerceIn(0f, 1f))
        fun centerX(index: Int) = slot * (index + 0.5f)

        listOf(0.0, 0.5, 1.0).forEach { fraction ->
            val gy = y(maxValue * fraction)
            drawLine(colors.outlineVariant, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        drawText(measurer, formatBytes(maxValue.toLong()), Offset(0f, 0f), style = labelStyle)

        days.indexOfFirst { it.date == today }.takeIf { it >= 0 }?.let { index ->
            drawRect(colors.primaryContainer, Offset(slot * index, top), Size(slot, plotHeight))
        }

        when (mode) {
            ChartMode.DAILY -> {
                days.forEachIndexed { index, day ->
                    val value = day.bytes ?: return@forEachIndexed
                    val barWidth = slot * 0.62f
                    val barTop = y(value)
                    val topLeft = Offset(centerX(index) - barWidth / 2, barTop)
                    val barSize = Size(barWidth, (top + plotHeight - barTop).coerceAtLeast(1.dp.toPx()))
                    if (day.estimated) {
                        drawRect(colors.primary, topLeft, barSize, style = Stroke(width = 1.5.dp.toPx()), alpha = 0.7f)
                    } else {
                        drawRect(colors.primary, topLeft, barSize)
                    }
                }
                dailyPaceBytes?.let {
                    drawLine(colors.error, Offset(0f, y(it)), Offset(size.width, y(it)),
                        strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                }
            }
            ChartMode.CUMULATIVE -> {
                limitBytes?.let { drawLine(colors.error, Offset(0f, y(it.toDouble())), Offset(size.width, y(it.toDouble())),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = dash) }
                if (lastIndex >= 0) {
                    val path = Path().apply {
                        moveTo(centerX(0), y(0.0))
                        for (index in 0..lastIndex) lineTo(centerX(index), y(days[index].cumulativeBytes ?: 0.0))
                    }
                    drawPath(path, colors.primary, style = Stroke(width = 2.5.dp.toPx()))
                    projectedEnd?.let {
                        drawLine(colors.primary, Offset(centerX(lastIndex), y(lastCumulative)),
                            Offset(centerX(days.lastIndex), y(it)), strokeWidth = 2.dp.toPx(), pathEffect = dash, alpha = 0.7f)
                    }
                    drawCircle(colors.primary, 4.dp.toPx(), Offset(centerX(lastIndex), y(lastCumulative)))
                }
            }
        }

        val labelEvery = if (days.size > 20) 5 else 3
        days.forEachIndexed { index, day ->
            if (index == 0 || index == days.lastIndex || day.date.dayOfMonth % labelEvery == 0 && days.lastIndex - index > 1) {
                val layout = measurer.measure(day.date.dayOfMonth.toString(), labelStyle)
                val x = (centerX(index) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, top + plotHeight + 4.dp.toPx()))
            }
        }
    }
}
