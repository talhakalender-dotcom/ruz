package com.talha.ruz.ui.nano

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talha.ruz.ai.AiStatus
import com.talha.ruz.ai.NanoTestCase
import com.talha.ruz.ai.NanoTestCases
import com.talha.ruz.platform.RuzDeps
import com.talha.ruz.ui.settings.AiStatusCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private data class NanoResult(val case: NanoTestCase, val output: String, val millis: Long, val failed: Boolean)

/** Gizli deneme ekranı: Nano'nun Türkçe başarımını Fold 7 üzerinde ölçer. */
@Composable
fun NanoTestScreen(deps: RuzDeps, onBack: () -> Unit) {
    val status by deps.ai.status.collectAsState()
    val results = remember { mutableStateListOf<NanoResult>() }
    var running by remember { mutableStateOf(false) }
    var freePrompt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val total = NanoTestCases.all.size

    suspend fun runOne(case: NanoTestCase): NanoResult {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val out = deps.ai.generate(case.prompt)
            NanoResult(case, out, mark.elapsedNow().inWholeMilliseconds, failed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NanoResult(case, "HATA: ${e.message}", mark.elapsedNow().inWholeMilliseconds, failed = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nano testi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AiStatusCard(deps) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$total deneme: başlık ve kategori, soru-cevap, metni bölme. " +
                            "Her biri Türkçe ve İngilizce yönergeyle çalışır.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !running && status == AiStatus.Available,
                            onClick = {
                                running = true
                                results.clear()
                                scope.launch {
                                    try {
                                        NanoTestCases.all.forEach { results.add(runOne(it)) }
                                    } finally {
                                        running = false
                                    }
                                }
                            },
                        ) { Text("Testleri çalıştır") }
                        OutlinedButton(
                            enabled = results.isNotEmpty() && !running,
                            onClick = { deps.platform.share(report(deps.appVersion, results)) },
                        ) { Text("Sonuçları paylaş") }
                    }
                    if (running) {
                        LinearProgressIndicator(
                            progress = { results.size / total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${results.size} / $total", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = freePrompt,
                        onValueChange = { freePrompt = it },
                        label = { Text("Serbest istem") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        enabled = !running && freePrompt.isNotBlank() && status == AiStatus.Available,
                        onClick = {
                            val case = NanoTestCase("Serbest", "-", freePrompt, freePrompt)
                            running = true
                            scope.launch {
                                try {
                                    results.add(0, runOne(case))
                                } finally {
                                    running = false
                                }
                            }
                        },
                    ) { Text("Gönder") }
                }
            }
            items(results) { r -> ResultCard(r) }
        }
    }
}

@Composable
private fun ResultCard(r: NanoResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${r.case.group} · ${r.case.variant} · ${r.millis} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(r.case.input, style = MaterialTheme.typography.bodyMedium)
            Text(
                r.output.ifBlank { "(boş cevap)" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (r.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun report(version: String, results: List<NanoResult>): String = buildString {
    appendLine("Ruz $version — Nano testi (${results.size} deneme)")
    results.forEach { r ->
        appendLine()
        appendLine("[${r.case.group} · ${r.case.variant} · ${r.millis} ms]")
        appendLine("Girdi: ${r.case.input}")
        appendLine("Cevap: ${r.output.ifBlank { "(boş)" }}")
    }
}
