package com.macrobro.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
    "Rate my eating today 👀",
    "What should I eat to hit my protein?",
    "Make me a 3-day full body plan",
    "Good pre-workout snack?",
    "How do I break a bench plateau?",
)

@Composable
fun BroScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val chat = vm.chat

    LaunchedEffect(chat.size, vm.broLoading) {
        val count = chat.size + if (vm.broLoading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Lime),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.FitnessCenter, null, tint = Bg) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Gym Bro", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                DimText(if (vm.broLoading) "typing…" else "Your AI coach. Knows today's log.")
            }
            if (chat.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Clear chat", tint = TextDim) }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (chat.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Bubble(ChatMsg("model", "Yo bro! 💪 I'm your Gym Bro. Ask me about training, food, or how today's macros are looking. Let's get those gains."))
                        if (!vm.hasKey) {
                            SectionCard {
                                Text("Add your Gemini API key to chat", fontWeight = FontWeight.SemiBold)
                                Button(onClick = onOpenSettings) { Text("Open Settings") }
                            }
                        } else {
                            SUGGESTIONS.forEach { sug ->
                                SuggestionChip(onClick = { vm.sendBro(sug) }, label = { Text(sug) })
                            }
                        }
                    }
                }
            }
            itemsIndexed(chat) { _, msg -> Bubble(msg) }
            if (vm.broLoading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        DimText("  Gym Bro is thinking…")
                    }
                }
            }
        }

        vm.broError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                placeholder = { Text("Ask Gym Bro…") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = {
                    vm.sendBro(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !vm.broLoading && vm.hasKey,
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = CardBg,
            title = { Text("Clear the chat?") },
            text = { Text("This deletes the whole conversation with Gym Bro.") },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
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
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 18.dp,
                    )
                )
                .background(if (mine) Lime else CardBg2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                miniMarkdown(msg.text),
                color = if (mine) Bg else TextMain,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}
