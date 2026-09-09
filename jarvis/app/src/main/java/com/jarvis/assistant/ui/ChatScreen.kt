package com.jarvis.assistant.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.chat.ChatLog
import com.jarvis.assistant.chat.ChatMessage
import com.jarvis.assistant.chat.Role
import com.jarvis.assistant.core.ServiceLocator

/**
 * The one screen, two modes:
 *  - full chat (MainActivity): top bar with toggles + model picker, message
 *    list, streaming bubbles, text input bar with mic;
 *  - overlay (AssistantActivity): translucent voice-first surface — arc
 *    reactor pulse, live transcript, recent turns, tap anywhere = barge-in.
 */
@Composable
fun ChatScreen(overlay: Boolean = false) {
    val vm = remember { ServiceLocator.viewModel }
        ?: return Text("JARVIS is initializing…", modifier = Modifier.padding(24.dp))
    val ui by vm.state.collectAsState()
    val messages by ServiceLocator.chatLog.messages.collectAsState()

    if (overlay) {
        OverlayContent(vm, ui, messages)
    } else {
        FullChatContent(vm, ui, messages)
    }
}

// ---------------------------------------------------------------------------
// Full chat
// ---------------------------------------------------------------------------

@Composable
private fun FullChatContent(
    vm: com.jarvis.assistant.chat.ChatViewModel,
    ui: com.jarvis.assistant.chat.ChatViewModel.UiState,
    messages: List<ChatMessage>,
) {
    var input by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // keep the newest content on screen
    LaunchedEffect(messages.size, ui.partialReply, ui.partialTranscript) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ---- top bar: title, model, toggles ----
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReactorDot(pulsing = ui.speaking || ui.listening || ui.generating)
                Spacer(Modifier.size(8.dp))
                Text(
                    "JARVIS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { modelMenuOpen = true }) {
                    Text(
                        ui.modelName ?: "no model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                    ServiceLocator.modelManager.list().forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            onClick = { vm.switchModel(model); modelMenuOpen = false },
                        )
                    }
                    if (ServiceLocator.modelManager.list().isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("no GGUF in files/models — run scripts/get_models.sh", fontSize = 12.sp) },
                            onClick = { modelMenuOpen = false },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ui.webEnabled,
                    onClick = { vm.toggleWeb() },
                    label = { Text(if (ui.webEnabled) "🌐 web on" else "✈ offline") },
                )
                FilterChip(
                    selected = ui.handsFree,
                    onClick = { vm.toggleHandsFree() },
                    label = { Text("🎧 hands-free") },
                )
                WakeChip(vm, ui)
            }

            Text(
                buildString {
                    append(ui.engineStatus)
                    append("  ·  stt: ").append(ui.sttName)
                    append("  ·  tts: ").append(ui.ttsName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ---- messages ----
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg -> MessageBubble(msg) }
            if (ui.partialReply.isNotEmpty()) {
                item {
                    MessageBubble(ChatMessage(Role.ASSISTANT, ui.partialReply), streaming = true)
                }
            }
            ui.toolStatus?.let {
                item { StatusLine(it) }
            }
            if (ui.listening) {
                item {
                    Text(
                        if (ui.partialTranscript.isBlank()) "listening…" else "“${ui.partialTranscript}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        // ---- input bar ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask JARVIS…") },
                shape = RoundedCornerShape(24.dp),
                singleLine = false,
                maxLines = 4,
            )
            Spacer(Modifier.size(8.dp))
            if (ui.generating) {
                IconButton(onClick = { vm.stopGeneration() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "stop", tint = MaterialTheme.colorScheme.primary)
                }
            } else if (ui.listening) {
                IconButton(onClick = { vm.stopListening() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "stop listening", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                IconButton(onClick = { vm.startListening() }) {
                    Icon(Icons.Filled.Mic, contentDescription = "speak", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(
                onClick = { if (input.isNotBlank()) { vm.send(input); input = "" } },
                enabled = input.isNotBlank() && !ui.generating,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "send",
                    tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun WakeChip(vm: com.jarvis.assistant.chat.ChatViewModel, ui: com.jarvis.assistant.chat.ChatViewModel.UiState) {
    // arming the wake service needs POST_NOTIFICATIONS on 13+
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setWake(granted) }

    FilterChip(
        selected = ui.wakeArmed,
        onClick = {
            if (!ui.wakeArmed && Build.VERSION.SDK_INT >= 33) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.setWake(!ui.wakeArmed)
            }
        },
        label = { Text(if (ui.wakeArmed) "👂 listening" else "👂 wake off") },
    )
}

// ---------------------------------------------------------------------------
// Overlay (voice-first)
// ---------------------------------------------------------------------------

@Composable
private fun OverlayContent(
    vm: com.jarvis.assistant.chat.ChatViewModel,
    ui: com.jarvis.assistant.chat.ChatViewModel.UiState,
    messages: List<ChatMessage>,
) {
    // auto-mic on open
    LaunchedEffect(Unit) { vm.onOverlayOpened() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { vm.bargeIn() } // tap anywhere = instant barge-in
            .background(Color(0xD90B1220)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            ArcReactor(
                active = ui.listening,
                speaking = ui.speaking,
                generating = ui.generating,
            )

            Spacer(Modifier.height(24.dp))

            val line = when {
                ui.listening && ui.partialTranscript.isNotBlank() -> "“${ui.partialTranscript}”"
                ui.listening -> "listening…"
                ui.generating && ui.partialReply.isNotBlank() -> ui.partialReply
                ui.generating -> "thinking…"
                ui.speaking -> "speaking…"
                ui.toolStatus != null -> ui.toolStatus
                else -> "say \"Hey JARVIS\" — or tap and speak"
            }
            Text(
                line,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 480.dp),
            )

            // recent turns (compact, translucent)
            val recent = messages.takeLast(ChatLog.OVERLAY_PREVIEW_TURNS)
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.alpha(0.75f).padding(horizontal = 24.dp),
                ) {
                    recent.forEach { msg ->
                        Text(
                            "${if (msg.role == Role.USER) "you" else "jarvis"}: ${msg.content.take(140)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun MessageBubble(msg: ChatMessage, streaming: Boolean = false) {
    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        SelectionContainer {
            Text(
                text = msg.content + if (streaming) " ▍" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp,
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
    )
}

/** Small status dot for the top bar. */
@Composable
private fun ReactorDot(pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "reactor")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (pulsing) 500 else 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reactor-alpha",
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .alpha(if (pulsing) alpha else 0.9f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/** The overlay's animated arc reactor. */
@Composable
private fun ArcReactor(active: Boolean, speaking: Boolean, generating: Boolean) {
    val transition = rememberInfiniteTransition(label = "arc")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (active || speaking) 1.12f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arc-scale",
    )
    val ringColor = when {
        speaking -> MaterialTheme.colorScheme.secondary
        generating -> MaterialTheme.colorScheme.primary
        active -> Color(0xFFA5F3FC)
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier.size(148.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(148.dp * scale)
                .clip(CircleShape)
                .border(3.dp, ringColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(2.dp, ringColor.copy(alpha = 0.7f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(44.dp * scale)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.85f))
        )
    }
}
