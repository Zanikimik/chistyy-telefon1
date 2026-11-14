package ru.chistyy.telefon

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val vm: CleanerViewModel by viewModels()

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onDeleteFinished(result.resultCode == RESULT_OK)
    }

    // На Android 11+ удалять чужие файлы можно только с подтверждения пользователя.
    private fun requestDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val sender = MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val permissions = if (Build.VERSION.SDK_INT >= 33) {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { vm.refresh() }
                LaunchedEffect(Unit) { permLauncher.launch(permissions) }

                CleanerScreen(vm = vm, onDelete = { requestDelete(it) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(vm: CleanerViewModel, onDelete: (List<Uri>) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Чистый телефон") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { FreeSpaceCard(vm.freeFraction, vm.lowSpace) }
            item { Text("Что можно удалить", style = MaterialTheme.typography.titleMedium) }
            items(Category.values().toList()) { category ->
                CategoryRow(
                    title = category.title,
                    size = vm.sizeOf(category),
                    checked = category in vm.selectedCategories,
                    onToggle = { vm.toggle(category) }
                )
            }
            item {
                Button(
                    onClick = { onDelete(vm.urisToDelete()) },
                    enabled = vm.selectedBytes > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Освободить ${formatSize(vm.selectedBytes)}")
                }
            }
            item {
                if (vm.loading) CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun FreeSpaceCard(freeFraction: Float, lowSpace: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Свободно ${(freeFraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { 1f - freeFraction },
                modifier = Modifier.fillMaxWidth()
            )
            if (lowSpace) {
                Text(
                    "Свободно меньше ${(CleanerViewModel.LOW_SPACE_LIMIT * 100).toInt()}%. Пора освободить место.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun CategoryRow(title: String, size: Long, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Text(title)
        }
        Text(formatSize(size), style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f ГБ", bytes / 1024.0 / 1024 / 1024)
    else -> "${bytes / 1024 / 1024} МБ"
}
