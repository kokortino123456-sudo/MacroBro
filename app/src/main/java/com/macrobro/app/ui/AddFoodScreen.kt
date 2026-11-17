package com.macrobro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.FoodLibrary
import com.macrobro.app.data.LibFood
import com.macrobro.app.data.MY_FOODS
import com.macrobro.app.data.RECENT
import com.macrobro.app.data.fmtG
import kotlin.math.roundToInt

/** Full-screen food picker: search the USDA library, your own foods and recent foods. */
@Composable
fun AddFoodScreen(vm: AppViewModel, meal: String, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var selected by remember { mutableStateOf<LibFood?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    val library = vm.libraryFoods
    val custom = vm.customFoods
    val recent = remember(vm.entries) { vm.recentLibFoods() }

    val browsing = query.isBlank() && filter == "All"
    val results = remember(query, filter, library, custom, recent) {
        if (browsing) emptyList() else {
            val pool: List<LibFood> = when (filter) {
                "All" -> custom + library
                MY_FOODS -> custom
                RECENT -> recent
                else -> library.filter { it.category == filter }
            }
            FoodLibrary.search(pool, query)
        }
    }
    val popular = remember(library) { library.filter { it.popular > 0 }.sortedBy { it.popular } }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 16.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Add to $meal", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(60) },
            placeholder = { Text("Search ${if (library.isEmpty()) "" else "%,d ".format(library.size)}foods…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            (listOf("All", MY_FOODS, RECENT) + FoodLibrary.CATEGORIES).forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c) })
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showQuickAdd = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Bolt, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Quick add")
                    }
                    OutlinedButton(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Create food")
                    }
                }
            }

            if (browsing) {
                if (custom.isNotEmpty()) {
                    item { ListHeader(MY_FOODS) }
                    items(custom, key = { "c${it.id}" }) { f -> FoodRow(f) { selected = f } }
                }
                if (recent.isNotEmpty()) {
                    item { ListHeader(RECENT) }
                    items(recent.take(6), key = { "r${it.name}" }) { f -> FoodRow(f) { selected = f } }
                }
                item { ListHeader("Popular") }
                if (library.isEmpty()) item { DimText("Loading foods…") }
                items(popular, key = { "p${it.popular}" }) { f -> FoodRow(f) { selected = f } }
            } else {
                if (results.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            DimText(if (filter == MY_FOODS && custom.isEmpty()) "You haven't created any foods yet." else "No foods match \"$query\".")
                            Button(onClick = { showCreate = true }) { Text("Create it as your own food") }
                        }
                    }
                }
                items(results, key = { "s${it.custom}${it.id}${it.name}${it.category}" }) { f -> FoodRow(f) { selected = f } }
                if (results.size >= 150) item { DimText("Showing the first 150 matches. Type more words to narrow it down.") }
            }

            item {
                Text(
                    "Food data: USDA FoodData Central (public domain), categories by TempoLife (CC-BY-4.0). Values per 100 g.",
                    color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }

    selected?.let { food ->
        AmountDialog(
            food = food,
            meal = meal,
            onDismiss = { selected = null },
            onAdd = { grams ->
                vm.addFoods(listOf(food.draftFor(grams)), meal, "manual")
                selected = null
                onClose()
            },
            onDelete = if (food.custom) ({ vm.deleteCustomFood(food.id); selected = null }) else null,
        )
    }
    if (showQuickAdd) {
        AddFoodDialog(
            meal = meal,
            recent = vm.recentFoods(),
            onDismiss = { showQuickAdd = false },
            onAdd = { draft ->
                vm.addFoods(listOf(draft), meal, "manual")
                showQuickAdd = false
                onClose()
            },
        )
    }
    if (showCreate) {
        CreateFoodDialog(
            initialName = query.trim().replaceFirstChar { it.uppercase() },
            onDismiss = { showCreate = false },
            onSave = { food ->
                showCreate = false
                selected = vm.addCustomFood(food)
            },
        )
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FoodRow(food: LibFood, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.display, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = when {
                food.category == RECENT -> "Last time ${food.portionG.roundToInt()} g"
                food.display != food.name -> food.name
                else -> food.category
            }
            Text(sub, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${fmtG(food.carbs)} g carbs", color = Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${food.kcal.roundToInt()} kcal · 100 g", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AmountDialog(
    food: LibFood,
    meal: String,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val start = if (food.portionG > 0) food.portionG else 100.0
    var gramsText by remember(food) { mutableStateOf(fmtG(start)) }
    val grams = gramsText.toDoubleSafe()
    val d = food.draftFor(grams)
    val f = grams / 100.0

    val chips = buildList {
        if (food.portionG > 0) add("${food.portionLabel} (${fmtG(food.portionG)} g)" to food.portionG)
        if (food.portionG > 0 && food.category != RECENT) add("2 × ${food.portionLabel}" to food.portionG * 2)
        listOf(50.0, 100.0, 150.0, 200.0, 250.0).forEach { add("${it.roundToInt()} g" to it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(food.display, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (food.display != food.name) DimText(food.name)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chips) { (label, g) ->
                        FilterChip(
                            selected = grams == g,
                            onClick = { gramsText = fmtG(g) },
                            label = { Text(label) },
                        )
                    }
                }
                NumberField(gramsText, { gramsText = it }, "Amount (g)", Modifier.fillMaxWidth(), decimal = true)

                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmtG(d.carbs)} g", color = Orange, fontWeight = FontWeight.Black, fontSize = 28.sp)
                        DimText("carbs")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${d.kcal}", color = Lime, fontWeight = FontWeight.Black, fontSize = 28.sp)
                        DimText("kcal")
                    }
                }
                Row {
                    NutrientCell("Protein", d.protein, Modifier.weight(1f))
                    NutrientCell("Fat", d.fat, Modifier.weight(1f))
                    NutrientCell("Sugar", food.sugar * f, Modifier.weight(1f))
                    NutrientCell("Fiber", food.fiber * f, Modifier.weight(1f))
                }
                if (food.fiber > 0) DimText("Net carbs (carbs − fiber): ${fmtG((d.carbs - food.fiber * f).coerceAtLeast(0.0))} g")
                DimText("Per 100 g: ${food.kcal.roundToInt()} kcal · C ${fmtG(food.carbs)} · P ${fmtG(food.protein)} · F ${fmtG(food.fat)}")
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete from My foods", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(enabled = grams > 0, onClick = { onAdd(grams) }) { Text("Add to $meal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NutrientCell(label: String, value: Double, modifier: Modifier) {
    Column(modifier) {
        Text("${fmtG(value)} g", fontWeight = FontWeight.SemiBold)
        Text(label, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun CreateFoodDialog(initialName: String, onDismiss: () -> Unit, onSave: (LibFood) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var portionLabel by remember { mutableStateOf("") }
    var portionG by remember { mutableStateOf("") }

    val p = protein.toDoubleSafe()
    val c = carbs.toDoubleSafe()
    val ft = fat.toDoubleSafe()
    val kcalValue = if (kcal.isNotBlank()) kcal.toDoubleSafe() else p * 4 + c * 4 + ft * 9
    val valid = name.isNotBlank() && kcalValue > 0 && p + c + ft <= 100.5

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text("Create food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                DimText("Copy the \"per 100 g\" column from the package label. It's saved to My foods.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f), decimal = true)
                    NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), decimal = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), decimal = true)
                    NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), decimal = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(sugar, { sugar = it }, "of which sugar", Modifier.weight(1f), decimal = true)
                    NumberField(fiber, { fiber = it }, "Fiber", Modifier.weight(1f), decimal = true)
                }
                DimText("Serving (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = portionLabel,
                        onValueChange = { portionLabel = it.take(20) },
                        label = { Text("e.g. 1 bar") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    NumberField(portionG, { portionG = it }, "grams", Modifier.weight(1f), decimal = true)
                }
                if (kcal.isBlank() && kcalValue > 0) DimText("≈ ${kcalValue.roundToInt()} kcal per 100 g from macros")
                if (p + c + ft > 100.5) Text("Protein + carbs + fat can't be over 100 g per 100 g.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val pg = portionG.toDoubleSafe()
                onSave(
                    LibFood(
                        name = name.trim(), display = name.trim(), category = MY_FOODS,
                        kcal = kcalValue, protein = p, carbs = c, fat = ft,
                        fiber = fiber.toDoubleSafe(), sugar = sugar.toDoubleSafe(),
                        portionLabel = if (pg > 0) portionLabel.trim().ifBlank { "1 serving" } else "",
                        portionG = pg,
                        custom = true,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
