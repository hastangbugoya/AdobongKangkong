package com.example.adobongkangkong.ui.debug

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.core.log.MeowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeowLogScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun load(file: File, maxLines: Int = 500): List<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        // Read all, keep last N lines (good enough for now)
        val all = file.readLines()
        all.takeLast(maxLines)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        lines = load(MeowLog.getLogFile(context))
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meow Logs") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = {
                        MeowLog.clear(context)
                        lines = emptyList()
                    }) { Text("Clear") }

                    TextButton(onClick = {
                        shareLogFile(context)
                    }) { Text("Share") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lines) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun shareLogFile(context: Context) {
    val file = MeowLog.getLogFile(context)
    if (!file.exists()) return

    // Minimal share: shares text content (no FileProvider needed)
    val text = runCatching { file.readText() }.getOrNull().orEmpty()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Meow Logs")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share logs"))
}
