package com.whisper.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whisper.mobile.R
import com.whisper.mobile.model.DownloadState
import com.whisper.mobile.model.ModelManager
import com.whisper.mobile.model.ModelType
import com.whisper.mobile.ui.theme.WhisperMobileTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelManager = ModelManager(this)

        setContent {
            WhisperMobileTheme {
                MainScreen(
                    modelManager = modelManager,
                    onEnableIme = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onSelectIme = {
                        val imm = getSystemService(InputMethodManager::class.java)
                        imm.showInputMethodPicker()
                    },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    modelManager: ModelManager,
    onEnableIme: () -> Unit,
    onSelectIme: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var selectedModel by remember { mutableStateOf(modelManager.getActiveModel()) }
    var currentLang by remember { mutableStateOf(modelManager.getLanguagePreference()) }
    var showDeleteDialog by remember { mutableStateOf<ModelType?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            // Model Selection
            Text(
                stringResource(R.string.select_model),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            ModelType.entries.forEach { model ->
                val isDownloaded = modelManager.isModelDownloaded(model)
                val isActive = selectedModel == model

                ModelCard(
                    model = model,
                    isDownloaded = isDownloaded,
                    isActive = isActive,
                    downloadState = if (isActive) downloadState else DownloadState.Idle,
                    onSelect = {
                        selectedModel = model
                        modelManager.setActiveModel(model)
                        if (!isDownloaded) {
                            scope.launch {
                                modelManager.downloadModel(model).collect { state ->
                                    downloadState = state
                                }
                            }
                        }
                    },
                    onDelete = { showDeleteDialog = model },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Language Selection
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "" to R.string.lang_auto,
                    "ar" to R.string.lang_ar,
                    "en" to R.string.lang_en,
                ).forEach { (code, labelRes) ->
                    OutlinedButton(
                        onClick = {
                            currentLang = code
                            modelManager.setLanguagePreference(code)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        RadioButton(
                            selected = currentLang == code,
                            onClick = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(labelRes),
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Enable & Select IME
            Text(
                stringResource(R.string.enable_ime),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.enable_ime_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onEnableIme,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Text(
                    stringResource(R.string.enable_ime),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSelectIme,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.select_ime))
            }

            Spacer(Modifier.height(16.dp))

            // Version
            Text(
                stringResource(R.string.version, "1.0"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_model)) },
            text = { Text("${model.name} (${model.displaySize})") },
            confirmButton = {
                TextButton(onClick = {
                    modelManager.deleteModel(model)
                    if (selectedModel == model) {
                        selectedModel = null
                    }
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelType,
    isDownloaded: Boolean,
    isActive: Boolean,
    downloadState: DownloadState,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val nameRes = if (model == ModelType.TINY) R.string.model_tiny else R.string.model_small
    val descRes = if (model == ModelType.TINY) R.string.model_tiny_desc else R.string.model_small_desc

    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isActive, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(nameRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isDownloaded) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Not downloaded",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Download progress
        AnimatedVisibility(visible = downloadState is DownloadState.Downloading) {
            val progress = (downloadState as? DownloadState.Downloading)?.progress ?: 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        AnimatedVisibility(visible = downloadState is DownloadState.Error) {
            Text(
                (downloadState as? DownloadState.Error)?.message ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
