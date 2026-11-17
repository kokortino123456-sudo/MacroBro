package com.macrobro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
fun MacroBar(label: String, value: Double, goal: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextDim, fontSize = 12.sp)
        Text(
            "${value.toInt()} / ${goal} g",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (goal <= 0) 0f else (value / goal).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = CardBg2,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val filtered = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.replace(',', '.')
            onChange(filtered.take(7))
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    )
}

fun String.toDoubleSafe(): Double = this.replace(',', '.').toDoubleOrNull() ?: 0.0
fun String.toIntSafe(): Int = this.toDoubleOrNull()?.toInt() ?: 0

/** Very small markdown → styled text: **bold**, # headings and bullet lists. */
fun miniMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { i, raw ->
        val t = raw.trimStart()
        var line = raw
        var heading = false
        when {
            t.startsWith("* ") || t.startsWith("- ") -> line = "• " + t.substring(2)
            t.startsWith("#") -> { line = t.trimStart('#').trim(); heading = true }
        }
        val parts = line.split("**")
        parts.forEachIndexed { j, part ->
            if (heading || j % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            else append(part)
        }
        if (i < lines.size - 1) append("\n")
    }
}

@Composable
fun DimText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = modifier)
}
