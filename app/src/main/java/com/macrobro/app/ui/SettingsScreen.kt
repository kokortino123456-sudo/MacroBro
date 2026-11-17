package com.macrobro.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.AppData
import com.macrobro.app.data.DEFAULT_MODEL
import com.macrobro.app.data.Settings
import com.macrobro.app.data.calculateGoals
import com.macrobro.app.data.fmtG
import com.macrobro.app.data.fmtL
import com.macrobro.app.data.fmtKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

private val ACTIVITY = listOf(
    1.2 to "Desk job",
    1.375 to "Light",
    1.55 to "Moderate",
    1.725 to "Very active",
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember(s) { mutableStateOf(s.apiKey) }
    var model by remember(s) { mutableStateOf(s.model) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testing by remember { mutableStateOf(false) }

    var sex by remember(s) { mutableStateOf(s.sex) }
    var age by remember(s) { mutableStateOf(s.age.toString()) }
    var height by remember(s) { mutableStateOf(s.heightCm.toString()) }
    var weight by remember(s) { mutableStateOf(fmtKg(s.weightKg)) }
    var activity by remember(s) { mutableStateOf(s.activity) }
    var goalType by remember(s) { mutableStateOf(s.goalType) }

    var kcal by remember(s) { mutableStateOf(s.goalKcal.toString()) }
    var protein by remember(s) { mutableStateOf(s.goalProtein.toString()) }
    var carbs by remember(s) { mutableStateOf(s.goalCarbs.toString()) }
    var fat by remember(s) { mutableStateOf(s.goalFat.toString()) }
    var water by remember(s) { mutableStateOf(fmtL(s.waterGoalMl)) }
    var saved by remember { mutableStateOf(false) }

    var pendingImport by remember { mutableStateOf<Pair<AppData, Settings>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun current() = s.copy(
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { DEFAULT_MODEL },
        sex = sex,
        age = age.toIntSafe().coerceIn(10, 100),
        heightCm = height.toIntSafe().coerceIn(100, 250),
        weightKg = weight.toDoubleSafe().coerceIn(30.0, 300.0),
        activity = activity,
        goalType = goalType,
        goalKcal = kcal.toIntSafe().coerceIn(800, 8000),
        goalProtein = protein.toIntSafe().coerceIn(0, 500),
        goalCarbs = carbs.toIntSafe().coerceIn(0, 1000),
        goalFat = fat.toIntSafe().coerceIn(0, 400),
        waterGoalMl = (water.toDoubleSafe() * 1000).toInt().coerceIn(500, 6000),
    )

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target ->
        if (target != null) {
            val text = vm.exportBackup()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(target)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                    } catch (e: Exception) { false }
                }
                Toast.makeText(context, if (ok) "Backup saved." else "Couldn't save the backup.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(source)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    } catch (e: Exception) { null }
                }
                try {
                    pendingImport = vm.parseBackup(text ?: throw IllegalArgumentException("Couldn't read that file."))
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Couldn't import that file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenTitle("Settings")

        SectionCard {
            SectionTitle("Gemini AI")
            DimText("Snap and Gym Bro use Google Gemini. Get a free key and paste it here. It stays on this phone.", size = 13)
            TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                Icon(Icons.Rounded.Key, null, tint = Lime)
                Spacer(Modifier.width(Space.s))
                Text("Get a free key at aistudio.google.com", color = Lime)
            }
            AppTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim(); testResult = null; saved = false },
                label = "API key",
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showKey = !showKey }) {
                        BlurSwap(showKey) { visible ->
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (visible) "Hide key" else "Show key",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = model,
                onValueChange = { model = it.trim(); testResult = null; saved = false },
                label = "Model",
                supporting = "Default: $DEFAULT_MODEL",
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                if (testing) "Testing…" else "Test key",
                {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = vm.testConnection(apiKey, model.ifBlank { DEFAULT_MODEL })
                        testing = false
                    }
                },
                enabled = apiKey.isNotBlank() && !testing,
                shape = RoundedCornerShape(Radius.inner),
            )
            testResult?.let { (ok, msg) -> StatusLine(ok, msg) }
        }

        SectionCard {
            SectionTitle("About you")
            ChoiceChips(listOf("male" to "Male", "female" to "Female"), sex, { sex = it; saved = false })
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NumberField(age, { age = it; saved = false }, "Age", Modifier.weight(1f))
                NumberField(height, { height = it; saved = false }, "Height cm", Modifier.weight(1f))
                NumberField(weight, { weight = it; saved = false }, "Weight kg", Modifier.weight(1f), decimal = true)
            }
            MutedText("Activity")
            val activityKey = ACTIVITY.minBy { abs(it.first - activity) }.first
            ChoiceChips(ACTIVITY.map { it.first to it.second }, activityKey, { activity = it; saved = false })
            MutedText("Goal")
            ChoiceChips(
                listOf("cut" to "Lose fat", "maintain" to "Maintain", "bulk" to "Build muscle"),
                goalType, { goalType = it; saved = false },
            )
            SecondaryButton(
                "Calculate my targets",
                {
                    val g = calculateGoals(current())
                    kcal = g[0].toString(); protein = g[1].toString(); carbs = g[2].toString(); fat = g[3].toString()
                    saved = false
                },
                shape = RoundedCornerShape(Radius.inner),
            )
            MutedText("A starting estimate. Adjust it after 2–3 weeks based on how your weight actually changes.")
        }

        SectionCard {
            SectionTitle("Daily targets")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NumberField(kcal, { kcal = it; saved = false }, "kcal", Modifier.weight(1f))
                NumberField(protein, { protein = it; saved = false }, "Protein g", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NumberField(carbs, { carbs = it; saved = false }, "Carbs g", Modifier.weight(1f))
                NumberField(fat, { fat = it; saved = false }, "Fat g", Modifier.weight(1f))
            }
            NumberField(water, { water = it; saved = false }, "Water (litres)", Modifier.fillMaxWidth(), decimal = true)
        }

        PrimaryButton(
            if (saved) "Saved" else "Save",
            { vm.updateSettings(current()); saved = true },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionCard {
            SectionTitle("Your data")
            DimText("Everything is stored only on this phone. Export a backup before you switch or reset phones.", size = 13)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton(
                    "Export", { exportLauncher.launch("macrobro-backup-${LocalDate.now()}.json") },
                    Modifier.weight(1f), icon = Icons.Rounded.FileDownload, shape = RoundedCornerShape(Radius.inner),
                )
                SecondaryButton(
                    "Import", { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    Modifier.weight(1f), icon = Icons.Rounded.FileUpload, shape = RoundedCornerShape(Radius.inner),
                )
            }
            MutedText("Backups include your log, own foods, weight, water, chat and targets. Never your API key.")
            SecondaryButton(
                "Delete all data", { confirmDelete = true },
                icon = Icons.Rounded.DeleteForever, shape = RoundedCornerShape(Radius.inner), color = ErrorRed,
            )
        }

        SectionCard(spacing = Space.s) {
            SectionTitle("About")
            val version = remember {
                try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
            }
            DimText("MacroBro $version. Free, no account, no ads, no tracking.", size = 13)
            MutedText(
                "Food data: USDA FoodData Central (public domain); categories by TempoLife (CC-BY-4.0); " +
                    "packaged foods from Open Food Facts (ODbL). Font: Barlow (SIL Open Font License). " +
                    "AI answers come from Google Gemini; photos and chat messages are sent there to get answers."
            )
        }
        Spacer(Modifier.height(Space.xl))
    }

    pendingImport?.let { (data, settings) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = Surface2,
            title = { Text("Replace your data?") },
            text = {
                Text(
                    "This backup has ${data.entries.size} logged foods, ${data.customFoods.size} own foods and " +
                        "${data.weights.size} weigh-ins. It replaces everything on this phone except your API key.",
                    color = TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyBackup(data, settings)
                    pendingImport = null
                    Toast.makeText(context, "Backup restored.", Toast.LENGTH_SHORT).show()
                }) { Text("Replace", color = Lime, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel", color = TextDim) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface2,
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This deletes your food log, own foods, weight, water and chat. Settings and your API key stay. " +
                        "This can't be undone, so export a backup first if you might want it back.",
                    color = TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAllData()
                    confirmDelete = false
                    Toast.makeText(context, "All data deleted.", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = ErrorRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextDim) } },
        )
    }
}
