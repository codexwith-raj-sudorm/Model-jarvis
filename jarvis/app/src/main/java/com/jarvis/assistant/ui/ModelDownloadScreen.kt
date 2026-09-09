package com.jarvis.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.ServiceLocator
import com.jarvis.assistant.llm.ModelDownloader

/**
 * In-app model downloader (P0): curated GGUF catalog, resumable downloads
 * with progress, one-tap activate. Reached from the model menu in the top
 * bar. Downloads survive closing this dialog (state lives in the
 * ModelDownloader singleton, not the composable).
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
        title = { Text("Download models") },
        text = {
            Column {
                Text(
                    "Wi-Fi recommended — downloads resume automatically if " +
                        "interrupted. Voice packs change what JARVIS sounds like.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.height(300.dp),
                ) {
                    items(downloader.catalog) { entry ->
                        val state = states[entry.fileName]
                        val installed = downloader.isInstalled(entry)

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${entry.sizeLabel} · ${entry.ramTier}" +
                                            if (installed) " · installed ✓" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when {
                                    state?.status == ModelDownloader.Status.RUNNING ->
                                        TextButton(onClick = { downloader.cancel(entry.fileName) }) {
                                            Text("cancel")
                                        }
                                    installed ->
                                        TextButton(onClick = {
                                            vm?.switchModel(downloader.installedFile(entry))
                                            onDismiss()
                                        }) { Text("activate") }
                                    else ->
                                        TextButton(onClick = { downloader.start(entry) }) {
                                            Text("download")
                                        }
                                }
                            }

                            when (state?.status) {
                                ModelDownloader.Status.RUNNING -> {
                                    Spacer(Modifier.height(4.dp))
                                    if (state.total > 0) {
                                        LinearProgressIndicator(
                                            progress = {
                                                (state.received.toFloat() / state.total)
                                                    .coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            "${state.received / 1048576} / ${state.total / 1048576} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    } else {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                }
                                ModelDownloader.Status.FAILED ->
                                    Text(
                                        "failed — tap download to retry (resumes)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                else -> {}
                            }
                        }
                    }

                    // ---- voice packs ------------------------------------------------

                    item {
                        Text(
                            "Voice packs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(packs.catalog) { entry ->
                        val state = voiceStates[entry.id]
                        val installed = packs.isInstalled(entry)
                        val active = packs.isActive(entry)

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${entry.langLabel} · ${entry.gender} · ${entry.sizeLabel}" +
                                            if (active) " · ● active" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when {
                                    state?.status == ModelDownloader.Status.RUNNING ->
                                        TextButton(onClick = { packs.cancel(entry.id) }) {
                                            Text("cancel")
                                        }
                                    installed && active -> {}
                                    installed ->
                                        TextButton(onClick = { packs.activate(entry) }) {
                                            Text("activate")
                                        }
                                    else ->
                                        TextButton(onClick = { packs.start(entry) }) {
                                            Text("download")
                                        }
                                }
                            }

                            when (state?.status) {
                                ModelDownloader.Status.RUNNING -> {
                                    Spacer(Modifier.height(4.dp))
                                    if (state.total > 0) {
                                        LinearProgressIndicator(
                                            progress = {
                                                (state.received.toFloat() / state.total)
                                                    .coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            "${state.received / 1048576} / ${state.total / 1048576} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    } else {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                }
                                ModelDownloader.Status.FAILED ->
                                    Text(
                                        "failed — tap download to retry (resumes)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                else -> {}
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("close") }
        },
    )
}
