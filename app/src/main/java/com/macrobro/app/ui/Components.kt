package com.macrobro.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- Surfaces ----------

private val borderBrush = Brush.verticalGradient(listOf(BorderTop, BorderBottom))

/** Filled surface with a soft alpha border that is brighter on top (dark-mode depth). */
fun Modifier.tile(shape: Shape = RoundedCornerShape(Radius.page), color: Color = Surface1): Modifier =
    this.clip(shape).background(color).border(1.dp, borderBrush, shape)

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = Space.l,
    spacing: Dp = Space.m,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tile(RoundedCornerShape(Radius.card))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
        if (sub != null) Text(sub, color = TextDim, fontSize = 14.sp)
    }
}

@Composable
fun DimText(text: String, modifier: Modifier = Modifier, size: Int = 14) {
    Text(text, color = TextDim, fontSize = size.sp, lineHeight = (size + 5).sp, modifier = modifier)
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = modifier)
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Icon + text, so status never relies on color alone. */
@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Good else ErrorRed,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.s))
        Text(text, fontSize = 14.sp, color = TextMain)
    }
}

// ---------- Data display ----------

/** A single ratio against a limit. The track is the same hue, lighter. */
@Composable
fun Meter(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
    ) {
        if (p > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(CircleShape).background(color))
    }
}

@Composable
fun MacroMeter(label: String, color: Color, value: Double, goal: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(Space.s))
            Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextMain)) {
                        append(value.roundToInt().toString())
                    }
                    withStyle(SpanStyle(color = TextMuted)) { append(" / $goal g") }
                },
                fontSize = 13.sp,
            )
        }
        Meter(if (goal <= 0) 0f else (value / goal).toFloat(), color)
    }
}

@Composable
fun Ring(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(size.width - w, size.height - w)
            drawArc(
                color = color.copy(alpha = 0.16f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arc, style = Stroke(width = w),
            )
            if (p > 0f) {
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                    topLeft = topLeft, size = arc, style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier.tile().padding(Space.m), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, maxLines = 1)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (sub != null) Text(sub, color = TextMuted, fontSize = 12.sp, maxLines = 1)
    }
}

// ---------- Controls ----------

@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        shape = RoundedCornerShape(Radius.inner),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = TextDim,
            selectedContainerColor = Lime,
            selectedLabelColor = Ink,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = BorderFlat,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

/** Horizontally scrolling chips whose edges fade out instead of being cut off. */
@Composable
fun <K> ChipRow(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    edge: Dp = Space.l,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        modifier = modifier
            .fillMaxWidth()
            .fadingEdges(edge)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = edge),
    ) {
        options.forEach { (key, label) -> AppChip(label, selected == key, { onSelect(key) }) }
    }
}

/** Choice chips that wrap onto a new line, for use inside cards and dialogs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <K> ChoiceChips(options: List<Pair<K, String>>, selected: K, onSelect: (K) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        options.forEach { (key, label) -> AppChip(label, selected == key, { onSelect(key) }) }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val outer = RoundedCornerShape(Radius.inner)
    Row(
        modifier
            .clip(outer)
            .background(Bg)
            .border(1.dp, BorderFlat, outer)
            .padding(Space.xs)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.tiny))
                    .background(if (sel) Surface3 else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = Space.m, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (sel) TextMain else TextDim,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(Radius.page),
) {
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
        colors = ButtonDefaults.buttonColors(
            containerColor = Lime,
            contentColor = Ink,
            disabledContainerColor = Surface3,
            disabledContentColor = TextMuted,
        ),
    ) {
        if (icon != null || loading) {
            BlurSwap(loading) { busy ->
                when {
                    busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                    icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(Space.s))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(Radius.page),
    color: Color = TextMain,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = TextMuted),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    focusedBorderColor = Lime,
    unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
    focusedLabelColor = Lime,
    unfocusedLabelColor = TextDim,
    cursorColor = Lime,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedLeadingIconColor = TextDim,
    unfocusedLeadingIconColor = TextDim,
    focusedTrailingIconColor = TextDim,
    unfocusedTrailingIconColor = TextDim,
)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    shape: Shape = RoundedCornerShape(Radius.inner),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = if (label != null) { { Text(label, maxLines = 1) } } else null,
        placeholder = if (placeholder != null) { { Text(placeholder, color = TextMuted, maxLines = 1) } } else null,
        leadingIcon = leading,
        trailingIcon = trailing,
        supportingText = if (supporting != null) { { Text(supporting, color = TextMuted) } } else null,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = shape,
        colors = fieldColors(),
    )
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    AppTextField(
        value = value,
        onValueChange = { new ->
            val filtered = new
                .filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }
                .replace(',', '.')
            onChange(filtered.take(7))
        },
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

// ---------- Motion & masking ----------

/**
 * Swaps content with a blur + scale + fade, so icons morph instead of popping.
 * Blur renders on Android 12+; older phones still get the fade and scale.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> BlurSwap(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.6f))
        },
        contentAlignment = Alignment.Center,
        label = "blurSwap",
    ) { state ->
        val blur by transition.animateDp(label = "blur") { if (it == EnterExitState.Visible) 0.dp else 6.dp }
        Box(Modifier.blur(blur, BlurredEdgeTreatment.Unbounded), contentAlignment = Alignment.Center) {
            content(state)
        }
    }
}

/** Masks the edges of scrolling content so it fades out instead of being cut off. */
fun Modifier.fadingEdges(
    edge: Dp = Space.l,
    horizontal: Boolean = true,
    start: Boolean = true,
    end: Boolean = true,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val e = edge.toPx()
        val length = if (horizontal) size.width else size.height
        if (e <= 0f || length <= 2 * e) return@drawWithContent
        val f = e / length
        val stops = arrayOf(
            0f to (if (start) Color.Transparent else Color.Black),
            f to Color.Black,
            1f - f to Color.Black,
            1f to (if (end) Color.Transparent else Color.Black),
        )
        val brush = if (horizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

// ---------- Text helpers ----------

fun String.toDoubleSafe(): Double = this.replace(',', '.').toDoubleOrNull() ?: 0.0
fun String.toIntSafe(): Int = this.toDoubleOrNull()?.toInt() ?: 0

/** Very small markdown to styled text: **bold**, # headings and bullet lists. */
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
