package com.macrobro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.data.DayStat
import com.macrobro.app.data.WeightEntry
import com.macrobro.app.data.fmtG
import com.macrobro.app.data.fmtKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val dayShort = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Daily calories as columns against the goal. One series, so one hue; the selected day
 * stays bright and the rest step back (emphasis). Bars: max 24dp, 4dp rounded tops, square base.
 */
@Composable
fun CaloriesChart(
    days: List<DayStat>,
    goal: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 160.dp,
) {
    val n = days.size.coerceAtLeast(1)
    val top = max(goal * 1.15f, (days.maxOfOrNull { it.kcal } ?: 0) * 1.08f).coerceAtLeast(500f)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .pointerInput(n) {
                    detectTapGestures { pos ->
                        onSelect((pos.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1))
                    }
                }
        ) {
            val slot = size.width / n
            val barW = min(24.dp.toPx(), slot * 0.62f)
            val radius = 4.dp.toPx()
            val hair = 1.dp.toPx()

            // Recessive hairline grid: baseline + halfway line.
            drawLine(Hairline, Offset(0f, size.height - hair / 2), Offset(size.width, size.height - hair / 2), hair)
            drawLine(Hairline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), hair)

            days.forEachIndexed { i, d ->
                if (d.kcal <= 0) return@forEachIndexed
                val h = size.height * (d.kcal / top)
                val left = slot * i + (slot - barW) / 2
                val r = min(radius, min(h, barW) / 2)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = size.height - h, right = left + barW, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                        )
                    )
                }
                drawPath(path, if (i == selected) Lime else Lime.copy(alpha = 0.42f))
            }

            // Goal: a solid hairline in muted ink, keyed below the chart instead of labelled on top of the bars.
            val goalY = size.height * (1f - goal / top)
            drawLine(TextDim, Offset(0f, goalY), Offset(size.width, goalY), hair)
        }
        XLabels(
            count = days.size,
            selected = selected,
            label = { i ->
                val d = days[i].date
                when {
                    days.size <= 7 -> d.format(dayShort)
                    (days.size - 1 - i) % 7 == 0 -> d.format(dayMonth)
                    else -> null
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s)) {
            Box(Modifier.width(16.dp).height(1.dp).background(TextDim))
            Spacer(Modifier.width(Space.s))
            Text("Daily goal ${goal.grouped()} kcal", color = TextDim, fontSize = 12.sp)
        }
    }
}

/** Labels centred under their slot, clamped so the first and last never overflow. */
@Composable
private fun XLabels(count: Int, selected: Int, label: (Int) -> String?) {
    if (count == 0) return
    BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).padding(top = Space.xs)) {
        val slot = maxWidth / count
        val w = 44.dp
        for (i in 0 until count) {
            val text = label(i)
            if (text != null) {
                val center = slot * i + slot / 2
                val x = (center - w / 2).coerceIn(0.dp, (maxWidth - w).coerceAtLeast(0.dp))
                Text(
                    text,
                    modifier = Modifier.offset(x = x).width(w),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = if (i == selected) TextMain else TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Weight over time. Points sit at their real dates, so gaps between weigh-ins stay honest.
 * 2dp line with a 10% wash, 8dp end dot with a 2dp surface ring.
 */
@Composable
fun WeightChart(
    points: List<WeightEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 160.dp,
) {
    if (points.size < 2) return
    val xs = points.map { LocalDate.parse(it.date).toEpochDay().toFloat() }
    val minX = xs.first()
    val spanX = max(1f, xs.last() - minX)
    val lo = floor(points.minOf { it.kg } - 0.5)
    var hi = ceil(points.maxOf { it.kg } + 0.5)
    if (hi - lo < 2) hi = lo + 2
    val ticks = listOf(hi, (hi + lo) / 2, lo)
    val gutter = 40.dp

    Column(modifier) {
        Row {
            // Y-axis ticks in muted ink.
            BoxWithConstraints(Modifier.width(gutter).height(plotHeight)) {
                ticks.forEach { v ->
                    val y = maxHeight * ((hi - v) / (hi - lo)).toFloat()
                    Text(
                        fmtKg(v),
                        modifier = Modifier.offset(y = (y - 8.dp).coerceIn(0.dp, maxHeight - 16.dp)),
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                    )
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(plotHeight)
                    .pointerInput(points.size) {
                        detectTapGestures { pos ->
                            val w = size.width.toFloat()
                            val nearest = xs.indices.minByOrNull { abs((xs[it] - minX) / spanX * w - pos.x) } ?: 0
                            onSelect(nearest)
                        }
                    }
            ) {
                val hair = 1.dp.toPx()
                fun px(i: Int) = (xs[i] - minX) / spanX * size.width
                fun py(kg: Double) = (size.height * ((hi - kg) / (hi - lo))).toFloat()

                ticks.forEach { v -> drawLine(Hairline, Offset(0f, py(v)), Offset(size.width, py(v)), hair) }

                val line = Path()
                points.forEachIndexed { i, p ->
                    if (i == 0) line.moveTo(px(i), py(p.kg)) else line.lineTo(px(i), py(p.kg))
                }
                val area = Path().apply {
                    points.forEachIndexed { i, p ->
                        if (i == 0) moveTo(px(i), py(p.kg)) else lineTo(px(i), py(p.kg))
                    }
                    lineTo(px(points.lastIndex), size.height)
                    lineTo(px(0), size.height)
                    close()
                }
                drawPath(area, WeightLine.copy(alpha = 0.10f))
                drawPath(line, WeightLine, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                val si = selected.coerceIn(0, points.lastIndex)
                val sx = px(si)
                val sy = py(points[si].kg)
                if (si != points.lastIndex) {
                    drawLine(TextMuted.copy(alpha = 0.6f), Offset(sx, 0f), Offset(sx, size.height), hair)
                }
                drawCircle(Surface1, radius = 6.dp.toPx(), center = Offset(sx, sy))
                drawCircle(WeightLine, radius = 4.dp.toPx(), center = Offset(sx, sy))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = gutter, top = Space.xs)) {
            Text(LocalDate.parse(points.first().date).format(dayMonth), color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(LocalDate.parse(points.last().date).format(dayMonth), color = TextMuted, fontSize = 11.sp)
        }
    }
}

/** Placeholder shown where a chart would be when there isn't enough data yet. */
@Composable
fun ChartEmpty(text: String, height: Dp = 120.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

