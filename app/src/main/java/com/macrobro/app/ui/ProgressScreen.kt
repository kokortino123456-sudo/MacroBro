package com.macrobro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.fmtG
import com.macrobro.app.data.fmtKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val longDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val s = vm.settings
    val today = LocalDate.now()
    val focus = LocalFocusManager.current

    var range by rememberSaveable { mutableIntStateOf(0) } // 0 = 7 days, 1 = 30 days
    val days = if (range == 0) 7 else 30
    val stats = remember(vm.entries, days) { vm.dayStats(days) }
    var selectedDay by remember(days) { mutableIntStateOf(days - 1) }

    var weightRange by rememberSaveable { mutableIntStateOf(0) } // 30 d, 90 d, all
    val weights = vm.weights
    val points = remember(weights, weightRange) {
        val from = when (weightRange) {
            0 -> today.minusDays(30)
            1 -> today.minusDays(90)
            else -> null
        }
        if (from == null) weights else weights.filter { it.date >= from.toString() }
    }
    var selectedPoint by remember(points) { mutableIntStateOf((points.size - 1).coerceAtLeast(0)) }
    var weightInput by remember { mutableStateOf("") }

    val streak = vm.streak()
    val last7 = remember(vm.entries) { vm.dayStats(7, today.minusDays(1)).filter { it.count > 0 } }
    val avgKcal = if (last7.isEmpty()) null else last7.sumOf { it.kcal } / last7.size
    val avgProtein = if (last7.isEmpty()) null else (last7.sumOf { it.protein } / last7.size).roundToInt()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenTitle("Progress")

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatTile(
                "Streak", "$streak ${if (streak == 1) "day" else "days"}", Modifier.weight(1f),
                sub = if (streak > 0) "logged in a row" else "log today to start",
            )
            StatTile("Avg intake", avgKcal?.grouped() ?: "–", Modifier.weight(1f), sub = "kcal, last 7 days")
            StatTile("Avg protein", avgProtein?.let { "$it g" } ?: "–", Modifier.weight(1f), sub = "goal ${s.goalProtein} g")
        }

        SectionCard {
            SectionTitle("Calories") {
                Segmented(listOf("7 days", "30 days"), range, { range = it })
            }
            val d = stats.getOrNull(selectedDay)
            if (d != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (d.count == 0) "–" else d.kcal.grouped(),
                        fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp,
                    )
                    Spacer(Modifier.width(Space.s))
                    Text(
                        if (d.count == 0) "nothing logged · ${d.date.format(longDay)}"
                        else "kcal · ${d.date.format(longDay)} · protein ${fmtG(d.protein)} g",
                        color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            val logged = stats.filter { it.count > 0 }
            val summary = if (logged.isEmpty()) "No calories logged in the last $days days."
            else "Calories for the last $days days. Average ${logged.sumOf { it.kcal } / logged.size} kcal on " +
                "${logged.size} logged days. Daily goal ${s.goalKcal} kcal."
            CaloriesChart(
                days = stats,
                goal = s.goalKcal,
                selected = selectedDay,
                onSelect = { selectedDay = it },
                modifier = Modifier.semantics { contentDescription = summary },
            )
        }

        SectionCard {
            SectionTitle("Weight") {
                Segmented(listOf("30 d", "90 d", "All"), weightRange, { weightRange = it })
            }
            val latest = weights.lastOrNull()
            if (latest != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${fmtKg(latest.kg)} kg", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
                    Spacer(Modifier.width(Space.s))
                    val changeText = if (points.size >= 2) {
                        val delta = points.last().kg - points.first().kg
                        val since = LocalDate.parse(points.first().date).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
                        if (abs(delta) < 0.05) "no change since $since"
                        else "${if (delta < 0) "−" else "+"}${fmtKg(abs(delta))} kg since $since"
                    } else "latest · ${LocalDate.parse(latest.date).format(longDay)}"
                    Text(changeText, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            if (points.size >= 2) {
                val sel = points[selectedPoint.coerceIn(0, points.lastIndex)]
                MutedText("${LocalDate.parse(sel.date).format(longDay)} · ${fmtKg(sel.kg)} kg")
                WeightChart(
                    points = points,
                    selected = selectedPoint,
                    onSelect = { selectedPoint = it },
                    modifier = Modifier.semantics {
                        contentDescription = "Weight from ${points.first().date} to ${points.last().date}: " +
                            "${fmtKg(points.first().kg)} to ${fmtKg(points.last().kg)} kg."
                    },
                )
            } else {
                ChartEmpty(
                    if (weights.isEmpty()) "Log your weight a few times to see your trend."
                    else "Not enough weigh-ins in this range yet."
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NumberField(weightInput, { weightInput = it }, "Today's weight (kg)", Modifier.weight(1f), decimal = true)
                val kg = weightInput.toDoubleSafe()
                PrimaryButton(
                    "Log",
                    {
                        vm.logWeight(today, kg)
                        weightInput = ""
                        focus.clearFocus()
                    },
                    enabled = kg in 30.0..300.0,
                    icon = Icons.Rounded.MonitorWeight,
                    shape = RoundedCornerShape(Radius.inner),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            MutedText("Weigh yourself at the same time each day, ideally in the morning. Daily ups and downs are normal; the trend is what counts.")

            if (weights.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
                Text("Weigh-ins", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                weights.asReversed().take(8).forEachIndexed { i, w ->
                    if (i > 0) HorizontalDivider(color = Hairline)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(LocalDate.parse(w.date).format(longDay), color = TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${fmtKg(w.kg)} kg", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        IconButton(onClick = { vm.deleteWeight(w.date) }) {
                            Icon(Icons.Rounded.Close, "Delete weigh-in", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xl))
    }
}
