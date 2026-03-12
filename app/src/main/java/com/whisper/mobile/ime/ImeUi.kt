package com.whisper.mobile.ime

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whisper.mobile.R
import com.whisper.mobile.audio.AudioRecorder
import com.whisper.mobile.ui.theme.WhisperMobileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImeUi(
    audioRecorder: AudioRecorder,
    onTranscribe: (FloatArray) -> String,
    language: String,
    onLanguageChange: (String) -> Unit,
    isModelLoaded: Boolean,
    onOpenSettings: () -> Unit,
) {
    WhisperMobileTheme {
        val isRecording by audioRecorder.isRecording.collectAsState()
        var statusText by remember { mutableStateOf("") }
        var currentLang by remember { mutableStateOf(language) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val ime = context as? WhisperIME

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar: language chips + settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LanguageChip("AR", "ar", currentLang) {
                            currentLang = it; onLanguageChange(it)
                        }
                        LanguageChip("EN", "en", currentLang) {
                            currentLang = it; onLanguageChange(it)
                        }
                        LanguageChip("Auto", "", currentLang) {
                            currentLang = it; onLanguageChange(it)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }

                // Status text
                Text(
                    text = when {
                        !isModelLoaded -> stringResource(R.string.no_model)
                        statusText.isNotEmpty() -> statusText
                        isRecording -> stringResource(R.string.recording)
                        else -> stringResource(R.string.ready)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                // Mic button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    if (isRecording) {
                        PulsingCircle()
                    }
                    LargeFloatingActionButton(
                        onClick = {
                            if (!isModelLoaded) return@LargeFloatingActionButton
                            if (isRecording) {
                                scope.launch {
                                    statusText = context.getString(R.string.transcribing)
                                    val audio = audioRecorder.stop()
                                    val text = withContext(Dispatchers.IO) {
                                        onTranscribe(audio)
                                    }
                                    if (text.isNotBlank()) {
                                        ime?.commitTranscription(text)
                                    }
                                    statusText = ""
                                }
                            } else {
                                audioRecorder.start()
                            }
                        },
                        containerColor = if (isRecording)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    langCode: String,
    currentLang: String,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = currentLang == langCode,
        onClick = { onSelect(langCode) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun PulsingCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                shape = CircleShape,
            )
    )
}
