package com.fileorganizer.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Screen(vm) }
            }
        }
    }
}

@Composable
private fun Screen(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { vm.addFolder(it) }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("File Organizer", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick one or more folders. Files will be sorted into Images, Videos, Documents, Audio and Archives.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(onClick = { picker.launch(null) }, enabled = !vm.running, modifier = Modifier.fillMaxWidth()) {
            Text("Add Folder")
        }

        Text("Selected folders (${vm.folders.size})", style = MaterialTheme.typography.titleMedium)

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.folders.isEmpty()) {
                item { Text("No folders selected yet.") }
            }
            items(vm.folders.toList()) { uri ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.displayName(uri), Modifier.weight(1f))
                        TextButton(onClick = { vm.removeFolder(uri) }, enabled = !vm.running) { Text("Remove") }
                    }
                }
            }
            vm.result?.let { r ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Done", style = MaterialTheme.typography.titleMedium)
                            Text("Moved: ${r.moved}")
                            Text("Skipped (unsupported type): ${r.skipped}")
                            Text("Failed: ${r.failed}")
                            r.errors.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }

        if (vm.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(vm.status, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }

        Button(
            onClick = { vm.start() },
            enabled = vm.folders.isNotEmpty() && !vm.running,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (vm.running) "Organizing…" else "Start Organizing")
        }
    }
}
