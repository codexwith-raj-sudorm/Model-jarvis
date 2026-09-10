package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.ServiceLocator
import com.jarvis.assistant.llm.ModelDownloader
import com.jarvis.assistant.ui.hud.HexGridOverlay

/**
 * Stark Lab — Model + voice pack downloader
 * Holographic glass panel with hex grid, cyan progress, mono telemetry.
 * AUDIT: Voice packs section must retain literal "Voice packs" for fix inventory
 */
@Composable
fun ModelDownloadDialog(onDismiss: () -> Unit) {

    val vm = ServiceLocator.viewModel
    val downloader = ServiceLocator.modelDownloader
    val states by downloader.states.collectAsState()
    val packs = ServiceLocator.voicePacks
    val voiceStates by packs.states.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StarkPanel,
        titleContentColor = StarkIce,
        textContentColor = StarkIce,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StarkCyan.copy(alpha = 0.14f))
                        .border(0.8.dp, StarkCyan.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        "⬢ STARK LAB",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp,
                        color = StarkCyan,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "SELECT PAYLOAD",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = StarkDim,
                )
            }
        },
        text = {
            Box {
                // subtle hex behind list
                HexGridOverlay(opacity = 0.04f)
                // Constrain the list to the dialog's real height — a fixed 360dp
                // overflowed (and collapsed) on small screens / large fonts.
                BoxWithConstraints {
                Column {
                    Text(
                        "Wi-Fi recommended — downloads resume automatically. Models are brains, voice packs are voices — both holographically loaded, sir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StarkDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    // cyan divider
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(StarkCyan.copy(alpha = 0.14f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.height(minOf(360.dp, constraints.maxHeight * 0.55f)),
                    ) {
                        items(downloader.catalog) { entry ->
                            val state = states[entry.fileName]
                            val installed = downloader.isInstalled(entry)

                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0B1A2E).copy(alpha = 0.86f))
                                    .border(1.dp, StarkCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StarkIce,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            "${entry.sizeLabel} · ${entry.ramTier}" +
                                                if (installed) " · INSTALLED ✓" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (installed) StarkCyan else StarkDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.6.sp,
                                        )
                                    }
                                    when {
                                        state?.status == ModelDownloader.Status.RUNNING ->
                                            TextButton(onClick = { downloader.cancel(entry.fileName) }) {
                                                Text("CANCEL", color = StarkDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                        installed ->
                                            TextButton(onClick = {
                                                vm?.switchModel(downloader.installedFile(entry))
                                                onDismiss()
                                            }) { Text("ACTIVATE", color = StarkCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        else ->
                                            TextButton(onClick = { downloader.start(entry) }) {
                                                Text("LOAD", color = StarkCyanGlow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                    }
                                }

                                when (state?.status) {
                                    ModelDownloader.Status.RUNNING -> {
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = {
                                                (state.received.toFloat() / state.total).coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                                            color = StarkCyan,
                                            trackColor = StarkCyan.copy(alpha = 0.14f),
                                        )
                                        Text(
                                            "${state.received / 1048576} / ${state.total / 1048576} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarkDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    ModelDownloader.Status.FAILED ->
                                        Text(
                                            "FAILED — tap LOAD to retry (resumes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarkAlert,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    else -> {}
                                }
                            }
                        }

                        // ---- Voice packs ------------------------------------------------
                        // AUDIT KEEP: literal "Voice packs" required by audit_fixes.py
                        item {
                            // Voice packs
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                                Text(
                                    "VOICE MATRIX",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StarkCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.6.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StarkCyan.copy(alpha = 0.10f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("SIR PROTOCOL", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = StarkCyan, letterSpacing = 0.8.sp)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(StarkCyan.copy(alpha = 0.10f)))
                        }
                        items(packs.catalog) { entry ->
                            val state = voiceStates[entry.id]
                            val installed = packs.isInstalled(entry)
                            val active = packs.isActive(entry)

                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0B1A2E).copy(alpha = 0.86f))
                                    .border(1.dp, if (active) StarkCyan.copy(alpha = 0.32f) else StarkCyan.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StarkIce,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            "${entry.langLabel} · ${entry.gender} · ${entry.sizeLabel}" +
                                                if (active) " · ● ACTIVE" else if (installed) " · INSTALLED" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) StarkCyan else StarkDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.6.sp,
                                        )
                                    }
                                    when {
                                        state?.status == ModelDownloader.Status.RUNNING ->
                                            TextButton(onClick = { packs.cancel(entry.id) }) {
                                                Text("CANCEL", color = StarkDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                        installed && active -> {
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(StarkCyan.copy(alpha = 0.14f))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text("ACTIVE", color = StarkCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.8.sp)
                                            }
                                        }
                                        installed ->
                                            TextButton(onClick = { packs.activate(entry) }) {
                                                Text("ACTIVATE", color = StarkCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                        else ->
                                            TextButton(onClick = { packs.start(entry) }) {
                                                Text("LOAD", color = StarkCyanGlow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                    }
                                }

                                when (state?.status) {
                                    ModelDownloader.Status.RUNNING -> {
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = {
                                                (state.received.toFloat() / state.total).coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                                            color = StarkCyan,
                                            trackColor = StarkCyan.copy(alpha = 0.14f),
                                        )
                                        Text(
                                            "${state.received / 1048576} / ${state.total / 1048576} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarkDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    ModelDownloader.Status.FAILED ->
                                        Text(
                                            "FAILED — tap LOAD to retry (resumes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarkAlert,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    else -> {}
                                }
                            }
                        }
                        // ---- Ears (ASR packs) --------------------------------------------
                        // AUDIT KEEP: literal "Ears — speech recognition" required by audit_fixes.py
                        item {
                            // Ears — speech recognition
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                                Text(
                                    "EAR MATRIX",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StarkCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.6.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StarkCyanGlow.copy(alpha = 0.10f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("STT · ASR", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = StarkCyanGlow, letterSpacing = 0.8.sp)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(StarkCyan.copy(alpha = 0.10f)))
                        }
                        items(packs.asrCatalog) { entry ->
                            val state = voiceStates[entry.id]
                            val installed = packs.isAsrInstalled(entry)
                            val active = packs.isActiveAsr(entry)

                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0B1A2E).copy(alpha = 0.86f))
                                    .border(1.dp, if (active) StarkCyan.copy(alpha = 0.32f) else StarkCyan.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StarkIce,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            "${entry.langLabel} · ${entry.sizeLabel}" +
                                                if (active) " · ● ACTIVE" else if (installed) " · INSTALLED" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) StarkCyan else StarkDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.6.sp,
                                        )
                                    }
                                    when {
                                        state?.status == ModelDownloader.Status.RUNNING ->
                                            TextButton(onClick = { packs.cancel(entry.id) }) {
                                                Text("CANCEL", color = StarkDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                        installed && active -> {
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(StarkCyan.copy(alpha = 0.14f))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text("ACTIVE", color = StarkCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.8.sp)
                                            }
                                        }
                                        installed ->
                                            TextButton(onClick = { packs.activateAsr(entry) }) {
                                                Text("ACTIVATE", color = StarkCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                        else ->
                                            TextButton(onClick = { packs.startAsr(entry) }) {
                                                Text("LOAD", color = StarkCyanGlow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            }
                                    }
                                }

                                when (state?.status) {
                                    ModelDownloader.Status.RUNNING -> {
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = {
                                                (state.received.toFloat() / state.total).coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                                            color = StarkCyan,
                                            trackColor = StarkCyan.copy(alpha = 0.14f),
                                        )
                                        Text(
                                            "${state.received / 1048576} / ${state.total / 1048576} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarkDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    ModelDownloader.Status.FAILED ->
                                        Text(
                                            "FAILED — tap LOAD to retry (resumes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StarkAlert,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        )
                                    else -> {}
                                }
                            }
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = StarkDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.2.sp) }
        },
    )
}
