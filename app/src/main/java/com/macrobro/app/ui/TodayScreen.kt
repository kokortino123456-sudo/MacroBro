package com.macrobro.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.FoodDraft
import com.macrobro.app.data.FoodEntry
import com.macrobro.app.data.MEALS
import com.macrobro.app.data.fmtG
import com.macrobro.app.data.fmtL
import com.macrobro.app.data.totalsOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun Int.grouped(): String = String.format(Locale.US, "%,d", this)

@Composable
fun TodayScreen(vm: AppViewModel, onSnap: () -> Unit) {
    var addMeal by rememberSaveable { mutableStateOf<String?>(null) }
    var scanOnOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }

    val openMeal = addMeal
    if (openMeal != null) {
        AddFoodScreen(
            vm = vm,
            initialMeal = openMeal,
            startScan = scanOnOpen,
            onClose = { addMeal = null; scanOnOpen = false },
        )
        return
    }

    val context = LocalContext.current
    val s = vm.settings
    val date = vm.selectedDate
    val dayEntries = vm.entriesFor(date)
    val t = totalsOf(dayEntries)
    val isToday = date == LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ScreenTitle(
                    dayTitle(date),
                    sub = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                    modifier = Modifier.weight(1f),
                )
                if (!isToday) {
                    TextButton(onClick = { vm.selectedDate = LocalDate.now() }) { Text("Today", color = Lime) }
                }
                Row(Modifier.tile(RoundedCornerShape(Radius.page))) {
                    IconButton(onClick = { vm.selectedDate = date.minusDays(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day")
                    }
                    IconButton(onClick = { vm.selectedDate = date.plusDays(1) }, enabled = !isToday) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day")
                    }
                }
            }
        }

        item { SummaryCard(t.kcal, t.protein, t.carbs, t.fat, s.goalKcal, s.goalProtein, s.goalCarbs, s.goalFat) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .tile(RoundedCornerShape(Radius.page))
                        .clickable { scanOnOpen = false; addMeal = vm.defaultMeal() }
                        .padding(start = Space.l),
                ) {
                    Icon(Icons.Rounded.Search, null, tint = TextDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.s))
                    Text("Search foods", color = TextDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scanOnOpen = true; addMeal = vm.defaultMeal() }) {
                        Icon(Icons.Rounded.QrCodeScanner, "Scan a barcode", tint = TextMain)
                    }
                }
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(Radius.page))
                        .background(Lime)
                        .clickable(onClick = onSnap),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CameraAlt, "Snap food with AI", tint = Ink) }
            }
        }

        item { WaterCard(ml = vm.waterFor(date), goalMl = s.waterGoalMl, onChange = { vm.addWater(date, it) }) }

        MEALS.forEach { meal ->
            val list = dayEntries.filter { it.meal == meal }
            item(key = "meal_$meal") {
                MealCard(
                    meal = meal,
                    entries = list,
                    onAdd = { scanOnOpen = false; addMeal = meal },
                    onEdit = { editing = it },
                    onCopy = {
                        val n = vm.copyMealFromPreviousDay(meal)
                        val msg = if (n == 0) "Nothing in $meal the day before." else "Copied $n food${if (n == 1) "" else "s"}."
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        item { Spacer(Modifier.height(Space.xl)) }
    }

    editing?.let { e ->
        EditEntryDialog(
            entry = e,
            onDismiss = { editing = null },
            onSave = { vm.updateEntry(it); editing = null },
            onDelete = { vm.deleteEntry(e.id); editing = null },
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
private fun SummaryCard(
    kcal: Int, protein: Double, carbs: Double, fat: Double,
    goalKcal: Int, goalP: Int, goalC: Int, goalF: Int,
) {
    val over = kcal > goalKcal
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(
                progress = if (goalKcal <= 0) 0f else kcal.toFloat() / goalKcal,
                color = if (over) Serious else Lime,
                modifier = Modifier.size(136.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(abs(goalKcal - kcal).grouped(), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp)
                    Text(if (over) "kcal over" else "kcal left", color = TextDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    "${kcal.grouped()} of ${goalKcal.grouped()} kcal",
                    fontSize = 14.sp, color = TextDim,
                )
                MacroMeter("Protein", Protein, protein, goalP)
                MacroMeter("Carbs", Carbs, carbs, goalC)
                MacroMeter("Fat", Fat, fat, goalF)
            }
        }
    }
}

@Composable
private fun WaterCard(ml: Int, goalMl: Int, onChange: (Int) -> Unit) {
    val glass = 250
    val cells = (goalMl / glass).coerceIn(4, 16)
    val filled = ml / glass
    SectionCard(spacing = Space.s) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, null, tint = Water, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
            Column(Modifier.weight(1f)) {
                Text("Water", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("${fmtL(ml)} of ${fmtL(goalMl)} L", color = TextDim, fontSize = 13.sp)
            }
            IconButton(onClick = { onChange(-glass) }, enabled = ml > 0) {
                Icon(Icons.Rounded.Remove, "Remove a glass")
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.inner))
                    .background(Surface3)
                    .clickable { onChange(glass) }
                    .padding(horizontal = Space.m, vertical = Space.s),
            ) { Text("+ 250 ml", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(cells) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i < filled) Water else Water.copy(alpha = 0.18f))
                )
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: String,
    entries: List<FoodEntry>,
    onAdd: () -> Unit,
    onEdit: (FoodEntry) -> Unit,
    onCopy: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(padding = Space.l, spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    if (entries.isEmpty()) "Nothing yet" else "${entries.sumOf { it.kcal }.grouped()} kcal",
                    color = TextDim, fontSize = 13.sp,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More for $meal", tint = TextDim) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Surface2) {
                    DropdownMenuItem(
                        text = { Text("Copy from previous day") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onCopy() },
                    )
                }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.inner))
                    .background(Lime)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Add to $meal", tint = Ink) }
        }
        entries.forEachIndexed { i, e ->
            if (i == 0) Spacer(Modifier.height(Space.s)) else HorizontalDivider(color = Hairline)
            EntryRow(e) { onEdit(e) }
        }
    }
}

@Composable
private fun EntryRow(e: FoodEntry, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.inner))
            .clickable(onClick = onClick)
            .padding(vertical = Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.source == "snap") {
                    Icon(Icons.Rounded.AutoAwesome, "Logged with AI", tint = TextDim, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Space.xs))
                }
                Text(e.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                (if (e.grams > 0) "${e.grams} g · " else "") +
                    "P ${fmtG(e.protein)} · C ${fmtG(e.carbs)} · F ${fmtG(e.fat)}",
                color = TextDim, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(e.kcal.grouped(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(" kcal", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun MealPicker(selected: String, onSelect: (String) -> Unit) {
    ChoiceChips(MEALS.map { it to it }, selected, onSelect)
}

@Composable
private fun EditEntryDialog(
    entry: FoodEntry,
    onDismiss: () -> Unit,
    onSave: (FoodEntry) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var meal by remember { mutableStateOf(entry.meal) }
    var grams by remember { mutableStateOf(if (entry.grams > 0) entry.grams.toString() else "") }
    var kcal by remember { mutableStateOf(entry.kcal.toString()) }
    var protein by remember { mutableStateOf(fmtG(entry.protein)) }
    var carbs by remember { mutableStateOf(fmtG(entry.carbs)) }
    var fat by remember { mutableStateOf(fmtG(entry.fat)) }
    val byWeight = entry.grams > 0

    // With a weight, editing grams rescales everything; without one, edit the numbers directly.
    val g = grams.toDoubleSafe()
    val factor = if (byWeight && entry.grams > 0) g / entry.grams else 1.0
    val preview = if (byWeight) entry.copy(
        grams = g.roundToInt(),
        kcal = (entry.kcal * factor).roundToInt(),
        protein = entry.protein * factor,
        carbs = entry.carbs * factor,
        fat = entry.fat * factor,
    ) else entry.copy(
        kcal = kcal.toIntSafe(),
        protein = protein.toDoubleSafe(),
        carbs = carbs.toDoubleSafe(),
        fat = fat.toDoubleSafe(),
    )
    val valid = name.isNotBlank() && (if (byWeight) g > 0 else preview.kcal >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Edit food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.verticalScroll(rememberScrollState())) {
                AppTextField(name, { name = it.take(60) }, "Name", Modifier.fillMaxWidth())
                if (byWeight) {
                    NumberField(grams, { grams = it }, "Amount (g)", Modifier.fillMaxWidth())
                    Text(
                        "${preview.kcal.grouped()} kcal · P ${fmtG(preview.protein)} · C ${fmtG(preview.carbs)} · F ${fmtG(preview.fat)}",
                        color = TextDim, fontSize = 14.sp,
                    )
                } else {
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), decimal = true)
                        NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), decimal = true)
                        NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), decimal = true)
                    }
                }
                MealPicker(meal) { meal = it }
                TextButton(onClick = onDelete) { Text("Delete from log", color = ErrorRed) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(preview.copy(name = name.trim(), meal = meal)) }) {
                Text("Save", color = if (valid) Lime else TextMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) } },
    )
}

/** Quick add: a one-off food with just numbers, no weight needed. */
@Composable
internal fun AddFoodDialog(
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
        containerColor = Surface2,
        title = { Text("Quick add to $meal") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (recent.isNotEmpty()) {
                    MutedText("Recent")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        items(recent, key = { it.id }) { r ->
                            AppChip(r.name, false, {
                                name = r.name
                                grams = if (r.grams > 0) r.grams.toString() else ""
                                kcal = r.kcal.toString()
                                protein = fmtG(r.protein)
                                carbs = fmtG(r.carbs)
                                fat = fmtG(r.fat)
                            })
                        }
                    }
                }
                AppTextField(name, { name = it.take(60) }, "Food name", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NumberField(grams, { grams = it }, "Grams", Modifier.weight(1f))
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), decimal = true)
                    NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), decimal = true)
                    NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), decimal = true)
                }
                if (kcal.isBlank() && kcalValue > 0) MutedText("About $kcalValue kcal from the macros")
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onAdd(FoodDraft(name.trim(), grams.toIntSafe(), kcalValue, p, c, f)) }) {
                Text("Add", color = if (valid) Lime else TextMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) } },
    )
}
