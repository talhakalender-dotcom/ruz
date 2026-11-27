package com.talha.ruz.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talha.ruz.ai.AiStatus
import com.talha.ruz.platform.RuzDeps
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(deps: RuzDeps, onBack: () -> Unit, onOpenNanoTest: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AiStatusCard(deps)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Geliştirici", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Telefondaki yapay zekânın Türkçe cümlelerde ne kadar iyi çalıştığını dener.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenNanoTest) { Text("Nano testini aç") }
                }
            }
            Text(
                "Ruz ${deps.appVersion}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Yapay zekâ durumu; gerekirse indirme düğmesi. Nano test ekranında da kullanılır. */
@Composable
fun AiStatusCard(deps: RuzDeps) {
    val status by deps.ai.status.collectAsState()
    val bytes by deps.ai.downloadedBytes.collectAsState()
    val error by deps.ai.lastError.collectAsState()
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Yapay zekâ (telefondaki Gemini Nano)", style = MaterialTheme.typography.titleMedium)
            Text("Durum: ${status.label}", style = MaterialTheme.typography.bodyMedium)
            if (status == AiStatus.Downloading && bytes > 0) {
                Text("İnen: ${bytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
            }
            if (status == AiStatus.Error && error != null) {
                Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { deps.ai.refresh() } }) { Text("Yenile") }
                if (status == AiStatus.Downloadable) {
                    Button(onClick = { scope.launch { deps.ai.download() } }) { Text("İndir") }
                }
            }
        }
    }
}
