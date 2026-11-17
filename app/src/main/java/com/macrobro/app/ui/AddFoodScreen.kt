package com.macrobro.app.ui

import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import com.macrobro.app.data.fmt1
import com.macrobro.app.data.LabelValues
import com.macrobro.app.ImageUtil
import androidx.core.content.FileProvider
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.macrobro.app.data.BarcodeResult
import com.macrobro.app.data.FoodLibrary
import com.macrobro.app.data.LibFood
import com.macrobro.app.data.MEALS
import com.macrobro.app.data.MY_FOODS
import com.macrobro.app.data.RECENT
import com.macrobro.app.data.fmtG
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class CreateDraft(val name: String, val barcode: String, val intro: String?)

private const val SHOPS = "CZ/SK shops"

/** Full-screen food picker: USDA library, your own foods, recent foods, barcodes and online products. */
@Composable
fun AddFoodScreen(vm: AppViewModel, initialMeal: String, startScan: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    var mealMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var selected by remember { mutableStateOf<LibFood?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var createDraft by remember { mutableStateOf<CreateDraft?>(null) }
    var typeBarcode by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var lookupBusy by remember { mutableStateOf(false) }
    var autoScanned by rememberSaveable { mutableStateOf(false) }

    var online by remember { mutableStateOf<List<LibFood>>(emptyList()) }
    var onlineFor by remember { mutableStateOf("") }
    var onlineBusy by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }

    var shopResults by remember { mutableStateOf<List<LibFood>>(emptyList()) }
    var shopFor by remember { mutableStateOf("") }
    LaunchedEffect(query, filter, vm.productCount) {
        val q = query.trim()
        if (q.length < 2 || vm.productCount == 0 || (filter != "All" && filter != SHOPS)) {
            shopResults = emptyList()
            shopFor = q
            return@LaunchedEffect
        }
        delay(250) // wait until typing pauses
        shopResults = vm.searchProducts(q)
        shopFor = q
    }

    BackHandler(onBack = onClose)

    fun handleCode(code: String) {
        lookupBusy = true
        notice = null
        scope.launch {
            try {
                when (val r = vm.lookupBarcode(code)) {
                    is BarcodeResult.Found -> selected = r.food
                    is BarcodeResult.NoNutrition -> createDraft = CreateDraft(
                        r.name, code, "\"${r.name}\" is on Open Food Facts but has no nutrition info yet. Add it from the label.",
                    )
                    BarcodeResult.NotFound -> createDraft = CreateDraft(
                        "", code, "Barcode $code isn't on Open Food Facts yet. Add it from the label and the next scan is instant.",
                    )
                }
            } catch (e: Exception) {
                notice = e.message ?: "Couldn't look up that barcode."
            } finally {
                lookupBusy = false
            }
        }
    }

    fun scan() {
        notice = null
        BarcodeScanner.scan(context, onCode = { handleCode(it) }, onMessage = { notice = it })
    }

    fun searchOnline() {
        val q = query.trim()
        if (q.isEmpty() || onlineBusy) return
        onlineBusy = true
        onlineError = null
        scope.launch {
            try {
                online = vm.searchOnline(q)
                onlineFor = q
                if (online.isEmpty()) onlineError = "No packaged foods found online for \"$q\"."
            } catch (e: Exception) {
                onlineError = e.message ?: "Online search failed."
            } finally {
                onlineBusy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (startScan && !autoScanned) {
            autoScanned = true
            scan()
        }
    }

    val library = vm.libraryFoods
    val custom = vm.customFoods
    val recent = remember(vm.entries) { vm.recentLibFoods() }
    val browsing = query.isBlank() && filter == "All"
    val isCuisine = filter in FoodLibrary.CUISINES
    // World dishes / a single cuisine without a query: show every dish, grouped by country.
    val browsingDishes = query.isBlank() && (filter == FoodLibrary.WORLD || isCuisine)
    val dishes = remember(library) { library.filter { it.isDish } }
    val results = remember(query, filter, library, custom, recent) {
        if (browsing || browsingDishes) emptyList() else {
            val pool: List<LibFood> = when {
                filter == "All" -> custom + library
                filter == MY_FOODS -> custom
                filter == RECENT -> recent
                filter == SHOPS -> emptyList()
                filter == FoodLibrary.WORLD -> library.filter { it.isDish }
                filter in FoodLibrary.CUISINES -> library.filter { it.isDish && it.category == filter }
                else -> library.filter { it.category == filter && !it.isDish }
            }
            FoodLibrary.search(pool, query)
        }
    }
    val popular = remember(library) { library.filter { it.popular > 0 }.sortedBy { it.popular } }
    val chipOptions = remember(filter, vm.productCount) {
        val base = listOf("All", MY_FOODS, RECENT) + (if (vm.productCount > 0) listOf(SHOPS) else emptyList()) +
            listOf(FoodLibrary.WORLD, "Czech", "Polish", "German")
        val extra = if (filter in FoodLibrary.CUISINES && filter !in base) listOf(filter) else emptyList()
        (base + extra + FoodLibrary.CATEGORIES).map { it to it }
    }
    val showOnline = query.isNotBlank() && filter == "All"

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = Space.xs, end = Space.xs, top = Space.xs),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Add food", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.tiny)).clickable { mealMenu = true },
                    ) {
                        Text("to $meal", color = TextDim, fontSize = 14.sp)
                        Icon(Icons.Rounded.ExpandMore, "Change meal", tint = TextDim, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }, containerColor = Surface2) {
                        MEALS.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { meal = m; mealMenu = false })
                        }
                    }
                }
            }
            IconButton(onClick = { scan() }) { Icon(Icons.Rounded.QrCodeScanner, "Scan a barcode") }
        }

        AppTextField(
            value = query,
            onValueChange = { query = it.take(60) },
            label = null,
            placeholder = if (library.isEmpty()) "Search foods" else "Search ${library.size.grouped()} foods",
            leading = { Icon(Icons.Rounded.Search, null) },
            trailing = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } }
            } else null,
            shape = RoundedCornerShape(Radius.page),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
        )

        ChipRow(
            options = chipOptions,
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier.padding(vertical = Space.s),
        )

        if (lookupBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.xs)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Lime)
                Spacer(Modifier.width(Space.s))
                DimText("Looking up the barcode…")
            }
        }
        notice?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = Space.l, vertical = Space.xs)
                    .fillMaxWidth()
                    .tile(RoundedCornerShape(Radius.page), Surface2)
                    .padding(start = Space.m, top = Space.s, bottom = Space.s),
            ) {
                Icon(Icons.Rounded.Info, null, tint = TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    Text(msg, fontSize = 14.sp)
                    Text(
                        "Type the barcode instead",
                        color = Lime, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { typeBarcode = true; notice = null }.padding(top = Space.xs),
                    )
                }
                IconButton(onClick = { notice = null }) { Icon(Icons.Rounded.Close, "Dismiss", tint = TextDim) }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.xs, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    PrimaryButton(
                        "Scan a barcode", { scan() }, Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.QrCodeScanner, loading = lookupBusy,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        SecondaryButton("Quick add", { showQuickAdd = true }, Modifier.weight(1f), icon = Icons.Rounded.Bolt)
                        SecondaryButton("Create food", { createDraft = CreateDraft(query.trim().replaceFirstChar { it.uppercase() }, "", null) }, Modifier.weight(1f), icon = Icons.Rounded.Add)
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
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        ListHeader("Dishes by country")
                        CuisineChips { filter = it }
                    }
                }
                item { ListHeader("Popular") }
                if (library.isEmpty()) item { DimText("Loading foods…") }
                items(popular, key = { "p${it.popular}" }) { f -> FoodRow(f) { selected = f } }
            } else if (browsingDishes) {
                val cuisines = if (isCuisine) listOf(filter) else FoodLibrary.CUISINES
                if (library.isEmpty()) item { DimText("Loading foods…") }
                cuisines.forEach { cuisine ->
                    val group = dishes.filter { it.category == cuisine }
                    if (group.isNotEmpty()) {
                        item(key = "h_$cuisine") { ListHeader(cuisine) }
                        items(group, key = { "w${it.id}" }) { f -> FoodRow(f) { selected = f } }
                    }
                }
                item {
                    MutedText(
                        "Dishes use values from official databases. A ≈ means no database has that exact dish, " +
                            "so the closest similar dish is used.",
                        Modifier.padding(top = Space.s),
                    )
                }
            } else if (filter == SHOPS) {
                when {
                    query.isBlank() -> item {
                        DimText("Type a product or brand, like \"mandle\" or \"Tatranky\", or scan a barcode.", Modifier.padding(vertical = Space.s))
                    }
                    shopResults.isEmpty() && shopFor == query.trim() -> item {
                        DimText("No shop products match \"$query\". Try fewer letters or scan the barcode.", Modifier.padding(vertical = Space.s))
                    }
                }
                items(shopResults, key = { "z${it.barcode}" }) { f -> FoodRow(f) { selected = f } }
                item {
                    MutedText(
                        "${vm.productCount.grouped()} products sold in Czech and Slovak shops, from Open Food Facts" +
                            (if (vm.productsBuilt.isNotBlank()) " (updated ${vm.productsBuilt})." else "."),
                        Modifier.padding(top = Space.s),
                    )
                }
            } else {
                // Library matches first, then shop products, then the rest of the library.
                val split = if (filter == "All") 8 else Int.MAX_VALUE
                val head = results.take(split)
                val tail = results.drop(split)
                if (results.isEmpty() && shopResults.isEmpty()) {
                    item {
                        DimText(
                            if (filter == MY_FOODS && custom.isEmpty()) "You haven't created any foods yet."
                            else "No foods match \"$query\" here.",
                            Modifier.padding(vertical = Space.s),
                        )
                    }
                }
                items(head, key = { "s${it.source}${it.id}${it.name}${it.display}" }) { f -> FoodRow(f) { selected = f } }
                if (shopResults.isNotEmpty()) {
                    item(key = "shops_header") { ListHeader("In Czech & Slovak shops") }
                    items(shopResults.take(15), key = { "z${it.barcode}" }) { f -> FoodRow(f) { selected = f } }
                    if (shopResults.size > 15) {
                        item(key = "shops_more") {
                            TextButton(onClick = { filter = SHOPS }) { Text("See all shop products", color = Lime) }
                        }
                    }
                }
                if (tail.isNotEmpty()) {
                    item(key = "more_header") { ListHeader("More foods") }
                    items(tail, key = { "s${it.source}${it.id}${it.name}${it.display}" }) { f -> FoodRow(f) { selected = f } }
                }
                if (results.size >= 150) item { MutedText("Showing the first 150 matches. Add a word to narrow it down.") }

                if (showOnline) {
                    if (online.isNotEmpty() && onlineFor == query.trim()) {
                        item { ListHeader("Packaged foods online") }
                        itemsIndexed(online, key = { i, f -> "o$i${f.barcode}" }) { _, f -> FoodRow(f) { selected = f } }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(top = Space.s)) {
                                SecondaryButton(
                                    if (onlineBusy) "Searching…" else "Search packaged foods online",
                                    { searchOnline() },
                                    Modifier.fillMaxWidth(),
                                    icon = Icons.Rounded.Public,
                                    enabled = !onlineBusy,
                                )
                                onlineError?.let { MutedText(it) }
                            }
                        }
                    }
                }
            }

            item {
                MutedText(
                    "Food data: USDA FoodData Central (public domain); Bundeslebensmittelschlüssel (BLS) 4.0, " +
                        "Max Rubner-Institut, CC BY 4.0 (energy calculated from macros with EU factors); " +
                        "USDA categories by TempoLife (CC-BY-4.0); packaged foods from Open Food Facts (ODbL). " +
                        "Values per 100 g.",
                    Modifier.padding(top = Space.l),
                )
            }
        }
    }

    selected?.let { food ->
        AmountDialog(
            food = food,
            meal = meal,
            onMealChange = { meal = it },
            onDismiss = { selected = null },
            onAdd = { grams ->
                vm.addFoods(listOf(food.draftFor(grams)), meal, "manual")
                selected = null
                onClose()
            },
            onDelete = if (food.custom) ({ vm.deleteCustomFood(food.id); selected = null }) else null,
            onSave = if (!food.custom && food.source == "off") ({ selected = vm.addCustomFood(food) }) else null,
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
    createDraft?.let { d ->
        CreateFoodDialog(
            vm = vm,
            initialName = d.name,
            barcode = d.barcode,
            intro = d.intro,
            onDismiss = { createDraft = null },
            onSave = { food ->
                createDraft = null
                selected = vm.addCustomFood(food)
            },
        )
    }
    if (typeBarcode) {
        TypeBarcodeDialog(
            onDismiss = { typeBarcode = false },
            onLookup = { code -> typeBarcode = false; handleCode(code) },
        )
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = Space.s))
}

@Composable
private fun FoodRow(food: LibFood, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tile(RoundedCornerShape(Radius.page))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m + Space.xs, vertical = Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.display, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = when {
                food.source == "recent" -> "Last time ${food.portionG.roundToInt()} g"
                food.note.isNotBlank() -> food.note
                food.display != food.name -> food.name
                else -> food.category
            }
            Text(sub, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.m))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Carbs, 6.dp)
                Spacer(Modifier.width(Space.xs))
                Text("${fmtG(food.carbs)} g carbs", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Text("${food.kcal.roundToInt()} kcal · 100 g", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountDialog(
    food: LibFood,
    meal: String,
    onMealChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (() -> Unit)?,
) {
    val start = if (food.portionG > 0) food.portionG else 100.0
    var gramsText by remember(food) { mutableStateOf(fmtG(start)) }
    val grams = gramsText.toDoubleSafe()
    val d = food.draftFor(grams)
    val f = grams / 100.0

    val chips = buildList {
        if (food.portionG > 0) add("${food.portionLabel} (${fmtG(food.portionG)} g)" to food.portionG)
        if (food.portionG > 0 && food.source != "recent") add("2 × ${food.portionLabel.lowercase()}" to food.portionG * 2)
        listOf(50.0, 100.0, 150.0, 200.0, 250.0).forEach { add("${it.roundToInt()} g" to it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(food.display, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    food.note.isNotBlank() -> MutedText(food.note)
                    food.display != food.name && food.source != "recent" -> MutedText(food.name)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    chips.forEach { (label, g) -> AppChip(label, grams == g, { gramsText = fmtG(g) }) }
                }
                NumberField(gramsText, { gramsText = it }, "Amount (g)", Modifier.fillMaxWidth(), decimal = true)

                Row {
                    BigFigure("Carbs", "${fmtG(d.carbs)} g", Carbs, Modifier.weight(1f))
                    BigFigure("Calories", d.kcal.grouped(), Lime, Modifier.weight(1f))
                }
                Row {
                    NutrientCell("Protein", d.protein, Modifier.weight(1f))
                    NutrientCell("Fat", d.fat, Modifier.weight(1f))
                    NutrientCell("Sugar", food.sugar * f, Modifier.weight(1f))
                    NutrientCell("Fiber", food.fiber * f, Modifier.weight(1f))
                }
                if (food.fiber > 0) {
                    if (food.fiberInCarbs) {
                        DimText("Net carbs (carbs minus fiber): ${fmtG((d.carbs - food.fiber * f).coerceAtLeast(0.0))} g", size = 13)
                    } else {
                        DimText("Carbs here already exclude fiber (EU style), so they are net carbs.", size = 13)
                    }
                }
                if (food.note.contains("≈")) {
                    MutedText("No database has this exact dish, so the values come from the closest similar dish.")
                }
                MutedText("Per 100 g: ${food.kcal.roundToInt()} kcal · C ${fmtG(food.carbs)} · P ${fmtG(food.protein)} · F ${fmtG(food.fat)}")
                ChoiceChips(MEALS.map { it to it }, meal, onMealChange)
                if (onSave != null) {
                    TextButton(onClick = onSave) { Text("Save to My foods", color = Lime) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete from My foods", color = ErrorRed) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = grams > 0, onClick = { onAdd(grams) }) {
                Text("Add to $meal", color = if (grams > 0) Lime else TextMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) } },
    )
}

@Composable
private fun BigFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 6.dp)
            Spacer(Modifier.width(Space.xs))
            Text(label, color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NutrientCell(label: String, value: Double, modifier: Modifier) {
    Column(modifier) {
        Text("${fmtG(value)} g", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(label, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun TypeBarcodeDialog(onDismiss: () -> Unit, onLookup: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val valid = code.length in 8..14
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Type a barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                DimText("The numbers under the barcode, usually 8 or 13 digits.")
                NumberField(code, { code = it.filter(Char::isDigit).take(14) }, "Barcode", Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onLookup(code) }) {
                Text("Look up", color = if (valid) Lime else TextMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) } },
    )
}

@Composable
private fun CreateFoodDialog(
    vm: AppViewModel,
    initialName: String,
    barcode: String,
    intro: String?,
    onDismiss: () -> Unit,
    onSave: (LibFood) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var portionLabel by remember { mutableStateOf("") }
    var portionG by remember { mutableStateOf("") }

    // Read the nutrition table from a photo with the chosen AI.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labelBusy by remember { mutableStateOf(false) }
    var labelError by remember { mutableStateOf<String?>(null) }
    var labelDone by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    fun applyLabel(v: LabelValues) {
        v.name?.let { if (name.isBlank() || name == initialName) name = it.take(60) }
        v.kcal?.let { kcal = fmt1(it) }
        v.protein?.let { protein = fmt1(it) }
        v.carbs?.let { carbs = fmt1(it) }
        v.fat?.let { fat = fmt1(it) }
        v.sugar?.let { sugar = fmt1(it) }
        v.fiber?.let { fiber = fmt1(it) }
        val pack = v.packG ?: 0.0
        val serving = v.servingG ?: 0.0
        when {
            pack in 1.0..250.0 -> { portionLabel = "1 pack"; portionG = fmt1(pack) }
            serving > 0 -> { portionLabel = "1 serving"; portionG = fmt1(serving) }
            pack > 0 -> { portionLabel = "1 pack"; portionG = fmt1(pack) }
        }
        labelDone = true
    }

    fun readPhoto(uri: Uri) {
        labelBusy = true
        labelError = null
        scope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { ImageUtil.load(context, uri, 1600) }
                    ?: throw IllegalStateException("Couldn't open that photo.")
                applyLabel(vm.readLabel(bmp))
            } catch (e: Exception) {
                labelError = e.message ?: "Couldn't read the label."
            } finally {
                labelBusy = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) readPhoto(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readPhoto(uri)
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "label_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            labelError = "No camera app found. Pick a photo instead."
        }
    }

    val p = protein.toDoubleSafe()
    val c = carbs.toDoubleSafe()
    val ft = fat.toDoubleSafe()
    val kcalValue = if (kcal.isNotBlank()) kcal.toDoubleSafe() else p * 4 + c * 4 + ft * 9
    val tooMuch = p + c + ft > 100.5
    val valid = name.isNotBlank() && kcalValue > 0 && !tooMuch

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Create food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (intro != null) Text(intro, fontSize = 14.sp)
                if (vm.hasKey) {
                    PrimaryButton(
                        if (labelBusy) "Reading the label…" else "Photo of the nutrition label",
                        { openCamera() },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.CameraAlt,
                        loading = labelBusy,
                        shape = RoundedCornerShape(Radius.inner),
                    )
                    TextButton(
                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !labelBusy,
                    ) { Text("or pick a label photo from the gallery", color = Lime, fontSize = 14.sp) }
                    labelError?.let { StatusLine(false, it) }
                    if (labelDone) StatusLine(true, "Filled from the label. Check the numbers, then save.")
                } else {
                    MutedText("Tip: add an AI key in Settings and the app can read the label from a photo.")
                }
                DimText("Or copy the \"per 100 g\" column from the package label. It's saved to My foods.", size = 13)
                AppTextField(name, { name = it.take(60) }, "Name", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f), decimal = true)
                    NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), decimal = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), decimal = true)
                    NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), decimal = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NumberField(sugar, { sugar = it }, "of which sugar", Modifier.weight(1f), decimal = true)
                    NumberField(fiber, { fiber = it }, "Fiber", Modifier.weight(1f), decimal = true)
                }
                MutedText("Serving (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    AppTextField(portionLabel, { portionLabel = it.take(20) }, "e.g. 1 bar", Modifier.weight(1f))
                    NumberField(portionG, { portionG = it }, "grams", Modifier.weight(1f), decimal = true)
                }
                if (barcode.isNotBlank()) MutedText("Barcode $barcode")
                if (kcal.isBlank() && kcalValue > 0) MutedText("About ${kcalValue.roundToInt()} kcal per 100 g from the macros")
                if (tooMuch) Text("Protein, carbs and fat can't add up to more than 100 g per 100 g.", color = ErrorRed, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val pg = portionG.toDoubleSafe()
                onSave(
                    LibFood(
                        name = name.trim(), display = name.trim(), category = MY_FOODS,
                        kcal = kcalValue, protein = p, carbs = c, fat = ft,
                        fiber = fiber.toDoubleSafe(), sugar = sugar.toDoubleSafe(),
                        portionLabel = if (pg > 0) portionLabel.trim().ifBlank { "1 serving" } else "",
                        portionG = pg,
                        custom = true,
                        barcode = barcode,
                    )
                )
            }) { Text("Save", color = if (valid) Lime else TextMuted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuisineChips(onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        FoodLibrary.CUISINES.forEach { c -> AppChip(c, false, { onPick(c) }) }
    }
}
