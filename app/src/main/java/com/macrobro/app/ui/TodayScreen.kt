package com.macrobro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.FoodDraft
import com.macrobro.app.data.FoodEntry
import com.macrobro.app.data.MEALS
import com.macrobro.app.data.fmtG
import com.macrobro.app.data.totalsOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(vm: AppViewModel, onSnap: () -> Unit) {
    val s = vm.settings
    val date = vm.selectedDate
    val dayEntries = vm.entriesFor(date)
    val t = totalsOf(dayEntries)
    var addMeal by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { vm.selectedDate = date.minusDays(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayTitle(date), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    DimText(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)))
                }
                IconButton(
                    onClick = { vm.selectedDate = date.plusDays(1) },
                    enabled = date.isBefore(LocalDate.now()),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                }
            }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val over = t.kcal > s.goalKcal
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (s.goalKcal <= 0) 0f else (t.kcal.toFloat() / s.goalKcal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = if (over) Orange else Lime,
                            strokeWidth = 12.dp,
                            trackColor = CardBg2,
                            strokeCap = StrokeCap.Round,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                abs(s.goalKcal - t.kcal).toString(),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                            )
                            DimText(if (over) "kcal over" else "kcal left")
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${t.kcal} / ${s.goalKcal} kcal", fontWeight = FontWeight.SemiBold)
                        MacroBar("Protein", t.protein, s.goalProtein, Sky)
                        MacroBar("Carbs", t.carbs, s.goalCarbs, Orange)
                        MacroBar("Fat", t.fat, s.goalFat, Pink)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSnap,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Snap your food", fontWeight = FontWeight.Bold)
            }
        }

        MEALS.forEach { meal ->
            val list = dayEntries.filter { it.meal == meal }
            item(key = "header_$meal") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Text(meal, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    DimText("${list.sumOf { it.kcal }} kcal")
                    IconButton(onClick = { addMeal = meal }) { Icon(Icons.Filled.Add, "Add to $meal", tint = Lime) }
                }
            }
            if (list.isEmpty()) {
                item(key = "empty_$meal") { DimText("Nothing yet", Modifier.padding(start = 4.dp)) }
            }
            items(list, key = { it.id }) { e -> EntryRow(e, onDelete = { vm.deleteEntry(e.id) }) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    addMeal?.let { meal ->
        AddFoodDialog(
            meal = meal,
            recent = vm.recentFoods(),
            onDismiss = { addMeal = null },
            onAdd = { draft ->
                vm.addFoods(listOf(draft), meal, "manual")
                addMeal = null
            },
        )
    }
}

private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}

@Composable
private fun EntryRow(e: FoodEntry, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.source == "snap") {
                    Icon(Icons.Filled.AutoAwesome, "From photo", tint = Lime, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DimText(
                (if (e.grams > 0) "${e.grams} g · " else "") +
                    "P ${fmtG(e.protein)} · C ${fmtG(e.carbs)} · F ${fmtG(e.fat)}"
            )
        }
        Text("${e.kcal}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        DimText(" kcal")
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Delete", tint = TextDim) }
    }
}

@Composable
private fun AddFoodDialog(
    meal: String,
    recent: List<FoodEntry>,
    onDismiss: () -> Unit,
    onAdd: (FoodDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    val p = protein.toDoubleSafe()
    val c = carbs.toDoubleSafe()
    val f = fat.toDoubleSafe()
    val kcalValue = if (kcal.isNotBlank()) kcal.toIntSafe() else (p * 4 + c * 4 + f * 9).roundToInt()
    val valid = name.isNotBlank() && kcalValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text("Add to $meal") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (recent.isNotEmpty()) {
                    DimText("Recent")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(recent, key = { it.id }) { r ->
                            AssistChip(
                                onClick = {
                                    name = r.name
                                    grams = if (r.grams > 0) r.grams.toString() else ""
                                    kcal = r.kcal.toString()
                                    protein = fmtG(r.protein)
                                    carbs = fmtG(r.carbs)
                                    fat = fmtG(r.fat)
                                },
                                label = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Food name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(grams, { grams = it }, "Grams", Modifier.weight(1f))
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), decimal = true)
                    NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), decimal = true)
                    NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), decimal = true)
                }
                if (kcal.isBlank() && kcalValue > 0) DimText("≈ $kcalValue kcal from macros")
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onAdd(FoodDraft(name.trim(), grams.toIntSafe(), kcalValue, p, c, f)) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
