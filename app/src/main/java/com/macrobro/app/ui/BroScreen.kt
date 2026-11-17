package com.macrobro.app.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrobro.app.AppViewModel
import com.macrobro.app.data.ChatMsg

private val SUGGESTIONS = listOf(
    "Rate my eating today",
    "What should I eat to hit my protein?",
    "How did my week go?",
    "Make me a 3-day full body plan",
    "Good pre-workout snack?",
)

@Composable
fun BroScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val chat = vm.chat

    LaunchedEffect(chat.size, vm.broLoading) {
        val count = chat.size + (if (vm.broLoading) 1 else 0) + (if (chat.isEmpty()) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs, top = Space.s),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(Radius.inner)).background(Lime),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.FitnessCenter, null, tint = Ink) }
            Column(Modifier.weight(1f).padding(start = Space.m)) {
                Text("Gym Bro", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    if (vm.broLoading) "typing…" else "Your AI coach. Sees your log, week and weight.",
                    color = TextDim, fontSize = 13.sp,
                )
            }
            if (chat.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear chat", tint = TextDim) }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingEdges(edge = Space.xl, horizontal = false, end = false),
            contentPadding = PaddingValues(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (chat.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Bubble(ChatMsg("model", "Yo bro! I'm your Gym Bro. Ask me about training, food, or how today's macros are looking. Let's get those gains."))
                        if (!vm.hasKey) {
                            SectionCard {
                                Text("Add an AI key (Gemini or OpenRouter) to chat", fontWeight = FontWeight.SemiBold)
                                PrimaryButton("Open Settings", onOpenSettings, shape = RoundedCornerShape(Radius.inner))
                            }
                        } else {
                            Spacer(Modifier.size(Space.xs))
                            SUGGESTIONS.forEach { sug ->
                                Text(
                                    sug,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .tile(RoundedCornerShape(Radius.page))
                                        .clickable { vm.sendBro(sug) }
                                        .padding(horizontal = Space.m + Space.xs, vertical = Space.m),
                                )
                            }
                        }
                    }
                }
            }
            itemsIndexed(chat) { _, msg -> Bubble(msg) }
            if (vm.broLoading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Space.xs)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Lime)
                        Spacer(Modifier.width(Space.s))
                        DimText("Gym Bro is thinking…", size = 13)
                    }
                }
            }
        }

        vm.broError?.let {
            Box(Modifier.padding(horizontal = Space.l)) { StatusLine(ok = false, text = it) }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(Space.m),
        ) {
            AppTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                label = null,
                placeholder = "Ask Gym Bro…",
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(Radius.card),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.s))
            val canSend = input.isNotBlank() && !vm.broLoading && vm.hasKey
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (canSend || vm.broLoading) Lime else Surface3)
                    .clickable(enabled = canSend) {
                        vm.sendBro(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                BlurSwap(vm.broLoading) { busy ->
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
                    else Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (canSend) Ink else TextMuted)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Surface2,
            title = { Text("Clear the chat?") },
            text = { Text("This deletes the whole conversation with Gym Bro.", color = TextDim) },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Clear", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = TextDim) } },
        )
    }
}

@Composable
private fun Bubble(msg: ChatMsg) {
    val mine = msg.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 310.dp)
                .then(
                    if (mine) Modifier
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                        .background(Lime)
                    else Modifier.tile(
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp),
                        Surface1,
                    )
                )
                .padding(horizontal = Space.m + Space.xs, vertical = Space.m - 2.dp),
        ) {
            Text(
                miniMarkdown(msg.text),
                color = if (mine) Ink else TextMain,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}
