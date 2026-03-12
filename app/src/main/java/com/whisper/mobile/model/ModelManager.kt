package com.whisper.mobile.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ModelType(val fileName: String, val displaySize: String, val url: String) {
    TINY(
        fileName = "ggml-tiny.bin",
        displaySize = "~75 MB",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    ),
    SMALL(
        fileName = "ggml-small.bin",
        displaySize = "~466 MB",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
    )
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    fun isModelDownloaded(type: ModelType): Boolean {
        return File(modelsDir, type.fileName).exists()
    }

    fun getModelPath(type: ModelType): String? {
        val file = File(modelsDir, type.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun deleteModel(type: ModelType) {
        File(modelsDir, type.fileName).delete()
    }

    fun downloadModel(type: ModelType): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        val targetFile = File(modelsDir, type.fileName)
        val tempFile = File(modelsDir, "${type.fileName}.tmp")

        try {
            val url = URL(type.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            // Handle redirects (HuggingFace often redirects)
            val responseCode = connection.responseCode
            val actualConnection = if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308
            ) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                val newConnection = URL(redirectUrl).openConnection() as HttpURLConnection
                newConnection.connectTimeout = 15_000
                newConnection.readTimeout = 30_000
                newConnection.connect()
                newConnection
            } else {
                connection
            }

            val totalBytes = actualConnection.contentLengthLong
            var downloadedBytes = 0L

            actualConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            emit(DownloadState.Downloading(downloadedBytes.toFloat() / totalBytes))
                        }
                    }
                }
            }

            actualConnection.disconnect()
            tempFile.renameTo(targetFile)
            emit(DownloadState.Complete)
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    fun getActiveModel(): ModelType? {
        val prefs = context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("active_model", null) ?: return null
        return try {
            ModelType.valueOf(name)
        } catch (_: Exception) {
            null
        }
    }

    fun setActiveModel(type: ModelType) {
        context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
            .edit().putString("active_model", type.name).apply()
    }

    fun getLanguagePreference(): String {
        return context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
            .getString("language", "") ?: ""
    }

    fun setLanguagePreference(lang: String) {
        context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
            .edit().putString("language", lang).apply()
    }
}
