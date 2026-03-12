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
import java.security.MessageDigest

enum class ModelType(val fileName: String, val displaySize: String, val url: String, val sha256: String) {
    TINY(
        fileName = "ggml-tiny.bin",
        displaySize = "~75 MB",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"
    ),
    SMALL(
        fileName = "ggml-small.bin",
        displaySize = "~466 MB",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
        sha256 = "1be3a9b2063867b937e64e2ec7483364a79571f104c43e168c1b5eb882f21b6d"
    )
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelManager(private val context: Context) {

    companion object {
        private val TRUSTED_HOSTS = setOf(
            "huggingface.co",
            "cdn-lfs.huggingface.co",
            "cdn-lfs-us-1.huggingface.co",
            "cdn-lfs-eu-1.huggingface.co",
        )
    }

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
            connection.instanceFollowRedirects = false
            connection.connect()

            // Handle redirects with domain validation
            val responseCode = connection.responseCode
            val actualConnection = if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308
            ) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                val redirectHost = URL(redirectUrl).host
                if (redirectHost !in TRUSTED_HOSTS) {
                    throw SecurityException("Untrusted redirect host: $redirectHost")
                }
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
            val digest = MessageDigest.getInstance("SHA-256")

            actualConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            emit(DownloadState.Downloading(downloadedBytes.toFloat() / totalBytes))
                        }
                    }
                }
            }

            actualConnection.disconnect()

            // Verify file integrity
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (actualHash != type.sha256) {
                tempFile.delete()
                emit(DownloadState.Error("Integrity check failed: file hash does not match expected value"))
                return@flow
            }

            tempFile.renameTo(targetFile)
            emit(DownloadState.Complete)
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadState.Error(e.message ?: "Download failed"))
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
