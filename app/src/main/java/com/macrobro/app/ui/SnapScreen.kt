package com.macrobro.app.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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

    val bmp = vm.snapBitmap
    val items = vm.snapItems
    val canAnalyze = (bmp != null || vm.snapNote.isNotBlank()) && vm.hasKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenTitle("Snap or describe", sub = "Photo, text, or both. The AI estimates calories and macros, you check them.")

        if (!vm.hasKey) {
            SectionCard {
                Text("Add your free Gemini API key first", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                DimText("Snap and Gym Bro run on Google Gemini. Setup takes about a minute.")
                PrimaryButton("Open Settings", onOpenSettings, shape = RoundedCornerShape(Radius.inner))
            }
        }

        if (bmp != null) {
            Box {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Your food photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(Radius.card)),
                )
                IconButton(
                    onClick = { vm.removeSnapPhoto() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.s)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.7f)),
                ) { Icon(Icons.Rounded.Close, "Remove photo", tint = TextMain) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SecondaryButton(if (bmp == null) "Camera" else "Retake", { openCamera() }, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt)
            SecondaryButton(
                "Gallery",
                { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                Modifier.weight(1f),
                icon = Icons.Rounded.PhotoLibrary,
            )
        }

        AppTextField(
            value = vm.snapNote,
            onValueChange = { vm.snapNote = it.take(400) },
            label = if (bmp == null) "Or describe what you ate" else "Details (optional)",
            placeholder = if (bmp == null) "2 eggs, 2 slices of toast with butter, coffee with milk" else "fried in butter, big plate",
            singleLine = false,
            maxLines = 5,
            shape = RoundedCornerShape(Radius.page),
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = when {
                vm.snapLoading -> "Analyzing…"
                items.isEmpty() -> "Estimate with AI"
                else -> "Estimate again"
            },
            onClick = { vm.analyzeSnap() },
            icon = Icons.Rounded.AutoAwesome,
            enabled = canAnalyze,
            loading = vm.snapLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        vm.snapError?.let { StatusLine(ok = false, text = it) }

        if (vm.snapNotes.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = TextDim, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(Space.s))
                DimText(vm.snapNotes)
            }
        }

        if (items.isNotEmpty()) {
            items.forEachIndexed { i, item -> SnapItemCard(item) { vm.updateSnapItem(i, it) } }

            val chosen = items.filter { it.selected }
            SectionCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Total", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${chosen.sumOf { it.kcal }.grouped()} kcal", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
                DimText(
                    "P ${fmtG(chosen.sumOf { it.protein })} g · C ${fmtG(chosen.sumOf { it.carbs })} g · F ${fmtG(chosen.sumOf { it.fat })} g",
                    size = 13,
                )
                MutedText("Add to")
                ChoiceChips(MEALS.map { it to it }, meal, { meal = it })
                val whenText = if (vm.selectedDate == LocalDate.now()) "today" else vm.selectedDate.toString()
                PrimaryButton(
                    "Add ${chosen.size} item${if (chosen.size == 1) "" else "s"} to $whenText",
                    { vm.addSnapToLog(meal); onDone() },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.inner),
                )
            }
        }

        if (bmp != null || items.isNotEmpty() || vm.snapNote.isNotBlank()) {
            TextButton(onClick = { vm.clearSnap() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Start over", color = TextDim)
            }
        }
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun SnapItemCard(item: SnapItem, onChange: (SnapItem) -> Unit) {
    var gramsText by remember(item.kcalPerG, item.proteinPerG) { mutableStateOf(item.grams.roundToInt().toString()) }
    SectionCard(spacing = Space.s) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onChange(item.copy(selected = it)) },
                colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Ink, uncheckedColor = TextDim),
            )
            AppTextField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it.take(60))) },
                label = null,
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.width(112.dp),
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("${item.kcal.grouped()} kcal", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                DimText("P ${fmtG(item.protein)} · C ${fmtG(item.carbs)} · F ${fmtG(item.fat)}", size = 13)
            }
        }
    }
}
