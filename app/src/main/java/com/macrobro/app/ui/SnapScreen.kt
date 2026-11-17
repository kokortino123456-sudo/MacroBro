package com.macrobro.app.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.MEALS
import com.macrobro.app.data.SnapItem
import com.macrobro.app.data.fmtG
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SnapScreen(vm: AppViewModel, onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var meal by rememberSaveable { mutableStateOf(vm.defaultMeal()) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) vm.loadSnapImage(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadSnapImage(uri)
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "snap_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.snapError = "No camera app found. Pick a photo from the gallery instead."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Snap Food AI", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        DimText("Take a photo of your meal and the AI estimates the calories and macros. Check the numbers before adding.")

        if (!vm.hasKey) {
            SectionCard {
                Text("Add your free Gemini API key first", fontWeight = FontWeight.SemiBold)
                DimText("The photo AI runs on Google Gemini. It takes about a minute to set up.")
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { openCamera() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Camera", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("Gallery")
            }
        }

        val bmp = vm.snapBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Your food photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            OutlinedTextField(
                value = vm.snapNote,
                onValueChange = { vm.snapNote = it.take(200) },
                label = { Text("Details (optional)") },
                placeholder = { Text("e.g. fried in butter, 2 eggs, big plate") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = { vm.analyzeSnap() },
                enabled = !vm.snapLoading && vm.hasKey,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (vm.snapLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Analyzing…")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.snapItems.isEmpty()) "Analyze food" else "Analyze again", fontWeight = FontWeight.Bold)
                }
            }
        }

        vm.snapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (vm.snapNotes.isNotBlank()) DimText("🤖 ${vm.snapNotes}")

        val items = vm.snapItems
        if (items.isNotEmpty()) {
            items.forEachIndexed { i, item -> SnapItemCard(item) { vm.updateSnapItem(i, it) } }

            val chosen = items.filter { it.selected }
            SectionCard {
                Row {
                    Text("Total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${chosen.sumOf { it.kcal }} kcal", fontWeight = FontWeight.Bold, color = Lime)
                }
                DimText(
                    "P ${fmtG(chosen.sumOf { it.protein })} g · C ${fmtG(chosen.sumOf { it.carbs })} g · F ${fmtG(chosen.sumOf { it.fat })} g"
                )
                DimText("Add to")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    MEALS.forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m, fontSize = 12.sp) })
                    }
                }
                Button(
                    onClick = { vm.addSnapToLog(meal); onDone() },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    val whenText = if (vm.selectedDate == LocalDate.now()) "today" else vm.selectedDate.toString()
                    Text("Add ${chosen.size} item${if (chosen.size == 1) "" else "s"} to $whenText")
                }
            }
        }

        if (bmp != null || items.isNotEmpty()) {
            TextButton(onClick = { vm.clearSnap() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Start over")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SnapItemCard(item: SnapItem, onChange: (SnapItem) -> Unit) {
    var gramsText by remember(item.kcalPerG, item.proteinPerG) { mutableStateOf(item.grams.roundToInt().toString()) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onChange(item.copy(selected = it)) },
                colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Bg),
            )
            OutlinedTextField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it.take(60))) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = gramsText,
                onChange = {
                    gramsText = it
                    val g = it.toDoubleSafe()
                    if (g > 0) onChange(item.copy(grams = g))
                },
                label = "Grams",
                modifier = Modifier.width(110.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${item.kcal} kcal", fontWeight = FontWeight.Bold, color = Lime, fontSize = 18.sp)
                DimText("P ${fmtG(item.protein)} · C ${fmtG(item.carbs)} · F ${fmtG(item.fat)}")
            }
        }
    }
}
