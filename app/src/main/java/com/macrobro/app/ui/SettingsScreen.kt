package com.macrobro.app.ui

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.DEFAULT_MODEL
import com.macrobro.app.data.calculateGoals
import com.macrobro.app.data.fmtG
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    var apiKey by remember(s) { mutableStateOf(s.apiKey) }
    var model by remember(s) { mutableStateOf(s.model) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    var sex by remember(s) { mutableStateOf(s.sex) }
    var age by remember(s) { mutableStateOf(s.age.toString()) }
    var height by remember(s) { mutableStateOf(s.heightCm.toString()) }
    var weight by remember(s) { mutableStateOf(fmtG(s.weightKg)) }
    var activity by remember(s) { mutableStateOf(s.activity) }
    var goalType by remember(s) { mutableStateOf(s.goalType) }

    var kcal by remember(s) { mutableStateOf(s.goalKcal.toString()) }
    var protein by remember(s) { mutableStateOf(s.goalProtein.toString()) }
    var carbs by remember(s) { mutableStateOf(s.goalCarbs.toString()) }
    var fat by remember(s) { mutableStateOf(s.goalFat.toString()) }
    var saved by remember { mutableStateOf(false) }

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
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 24.sp)

        SectionCard {
            Text("Gemini AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            DimText("Snap Food AI and Gym Bro use Google Gemini. Get a free key, paste it here. It stays on this phone.")
            TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                Text("Get a free key at aistudio.google.com →")
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim(); testResult = null },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.trim(); testResult = null },
                label = { Text("Model") },
                supportingText = { Text("Default: $DEFAULT_MODEL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedButton(
                enabled = apiKey.isNotBlank() && !testing,
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = vm.testConnection(apiKey, model.ifBlank { DEFAULT_MODEL })
                        testing = false
                    }
                },
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Test key")
            }
            testResult?.let { Text(it) }
        }

        SectionCard {
            Text("About you", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            ChipRow(listOf("male" to "Male", "female" to "Female"), sex) { sex = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(age, { age = it }, "Age", Modifier.weight(1f))
                NumberField(height, { height = it }, "Height cm", Modifier.weight(1f))
                NumberField(weight, { weight = it }, "Weight kg", Modifier.weight(1f), decimal = true)
            }
            DimText("Activity")
            val activityKey = ACTIVITY.minBy { abs(it.first - activity) }.first.toString()
            ChipRow(ACTIVITY.map { it.first.toString() to it.second }, activityKey) { activity = it.toDouble() }
            DimText("Goal")
            ChipRow(listOf("cut" to "Lose fat", "maintain" to "Maintain", "bulk" to "Build muscle"), goalType) { goalType = it }
            Button(onClick = {
                val g = calculateGoals(current())
                kcal = g[0].toString(); protein = g[1].toString(); carbs = g[2].toString(); fat = g[3].toString()
                saved = false
            }) { Text("Calculate my targets") }
            DimText("A starting estimate. Adjust it after 2–3 weeks based on how your weight actually changes.")
        }

        SectionCard {
            Text("Daily targets", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(kcal, { kcal = it; saved = false }, "kcal", Modifier.weight(1f))
                NumberField(protein, { protein = it; saved = false }, "Protein g", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(carbs, { carbs = it; saved = false }, "Carbs g", Modifier.weight(1f))
                NumberField(fat, { fat = it; saved = false }, "Fat g", Modifier.weight(1f))
            }
        }

        Button(
            onClick = { vm.updateSettings(current()); saved = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (saved) "Saved ✓" else "Save", fontWeight = FontWeight.Bold) }

        DimText("Your food log, chat and key are stored only on this phone. Photos and chat messages are sent to Google Gemini to get answers.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
