package com.vancartier.vvcmobileagentcore.modelruntime

import com.vancartier.vvcmobileagentcore.security.VvcHashCalculator
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val store: ModelStore) {
    suspend fun downloadAndInstall(
        descriptor: ModelDescriptor,
        onProgress: (ModelProgress) -> Unit = {}
    ): File {
        require(descriptor.artifactUrl.startsWith("https://")) { "Los modelos deben descargarse mediante HTTPS" }
        require(descriptor.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 inválido para ${descriptor.id}" }
        val partial = store.temporaryFile(descriptor.id, descriptor.version)
        partial.parentFile?.mkdirs()
        val connection = URL(descriptor.artifactUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.requestMethod = "GET"
        try {
            check(connection.responseCode in 200..299) { "Descarga HTTP ${connection.responseCode}" }
            val total = if (descriptor.sizeBytes > 0) descriptor.sizeBytes else connection.contentLengthLong
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(ModelProgress(descriptor.id, downloaded, total))
                }
            } }
            check(descriptor.sizeBytes <= 0 || partial.length() == descriptor.sizeBytes) { "Tamaño inesperado para ${descriptor.id}" }
            check(VvcHashCalculator.calculateFileSha256(partial).equals(descriptor.sha256, ignoreCase = true)) { "SHA-256 inválido para ${descriptor.id}" }
            return store.install(descriptor, partial)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally { connection.disconnect() }
    }
    companion object { private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 }
}
