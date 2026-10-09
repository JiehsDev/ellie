package com.example.jikan.coach

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelManager(private val context: Context) {
    private val modelDir: File
        get() = File(context.filesDir, "model")

    val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    private val partialModelFile: File
        get() = File(modelDir, "$MODEL_FILENAME.part")

    fun isModelAvailable(): Boolean {
        return modelFile.exists() && modelFile.length() >= MIN_MODEL_BYTES
    }

    fun getModelPath(): String {
        return modelFile.absolutePath
    }

    suspend fun ensureModelInstalled(onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isModelAvailable()) {
            onProgress(100)
            return@withContext true
        }

        modelDir.mkdirs()
        if (partialModelFile.exists()) {
            partialModelFile.delete()
        }

        val connection = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext false
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: MODEL_SIZE_BYTES
            connection.inputStream.use { input ->
                partialModelFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L
                    var bytesRead = input.read(buffer)
                    while (bytesRead >= 0) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        val progress = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 99)
                        onProgress(progress)
                        bytesRead = input.read(buffer)
                    }
                }
            }

            if (partialModelFile.length() < MIN_MODEL_BYTES) {
                partialModelFile.delete()
                return@withContext false
            }

            if (modelFile.exists()) {
                modelFile.delete()
            }
            val installed = partialModelFile.renameTo(modelFile)
            if (installed) {
                onProgress(100)
            }
            installed
        } catch (e: Exception) {
            partialModelFile.delete()
            false
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MODEL_REPOSITORY = "mfuntowicz/SmolLM2-360M-Instruct-Q4_K_M-GGUF"
        const val MODEL_FILENAME = "smollm2-360m-instruct-q4_k_m.gguf"
        const val MODEL_VERSION = "SmolLM2-360M-Instruct-Q4_K_M"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/$MODEL_REPOSITORY/resolve/main/$MODEL_FILENAME"

        private const val MODEL_SIZE_BYTES = 271L * 1024L * 1024L
        private const val MIN_MODEL_BYTES = 250L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
