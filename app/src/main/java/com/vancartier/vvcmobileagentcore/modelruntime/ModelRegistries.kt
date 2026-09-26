package com.vancartier.vvcmobileagentcore.modelruntime

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature

class LocalModelRegistry(private val file: java.io.File) : ModelRegistry {
    override suspend fun fetch(): ModelManifest = if (!file.isFile) ModelManifest(emptyList()) else parseManifest(file.readText())
    fun save(manifest: ModelManifest) { file.parentFile?.mkdirs(); file.writeText(manifest.toJson().toString(2)) }
}

class HttpModelRegistry(private val endpoint: String) : ModelRegistry {
    override suspend fun fetch(): ModelManifest {
        require(endpoint.startsWith("https://")) { "El registro remoto debe usar HTTPS" }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        return try {
            check(connection.responseCode in 200..299) { "Registro HTTP ${connection.responseCode}" }
            parseManifest(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}

data class SignedManifestEnvelope(val payload: String, val signature: String)

class SignedHttpModelRegistry(private val endpoint: String, private val verifier: Ed25519ManifestVerifier) : ModelRegistry {
    override suspend fun fetch(): ModelManifest {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        return try {
            check(connection.responseCode in 200..299) { "Registro firmado HTTP ${connection.responseCode}" }
            val envelope = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val payload = envelope.getString("payload")
            check(verifier.verify(payload, envelope.getString("signature"))) { "Firma Ed25519 inválida" }
            parseManifest(payload)
        } finally { connection.disconnect() }
    }
}

class Ed25519ManifestVerifier(private val publicKey: PublicKey) {
    fun verify(payload: String, signatureBase64: String): Boolean = runCatching {
        Signature.getInstance("Ed25519").apply {
            initVerify(publicKey)
            update(payload.toByteArray(StandardCharsets.UTF_8))
        }.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
    }.getOrDefault(false)
}

private fun parseManifest(raw: String): ModelManifest {
    val json = JSONObject(raw)
    val array = json.optJSONArray("models") ?: JSONArray()
    val models = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(ModelDescriptor(
                id = item.getString("id"), version = item.getString("version"),
                artifactUrl = item.optString("artifactUrl"), sha256 = item.getString("sha256"),
                sizeBytes = item.optLong("sizeBytes", -1), runtime = item.optString("runtime", "litert"),
                inputContract = item.optString("inputContract"), outputContract = item.optString("outputContract"),
                capabilities = item.optJSONArray("capabilities").toStringSet(),
                minAppVersion = item.optString("minAppVersion", "1.0.0"),
                minMemoryMb = item.optInt("minMemoryMb", 0), enabled = item.optBoolean("enabled", true)
            ))
        }
    }
    return ModelManifest(models, json.optString("generatedAt").takeIf { it.isNotBlank() })
}

private fun JSONArray?.toStringSet(): Set<String> = if (this == null) emptySet() else buildSet { for (i in 0 until length()) add(getString(i)) }
private fun ModelManifest.toJson(): JSONObject = JSONObject().apply {
    put("generatedAt", generatedAt ?: JSONObject.NULL)
    put("models", JSONArray(models.map { model -> JSONObject().apply {
        put("id", model.id); put("version", model.version); put("artifactUrl", model.artifactUrl)
        put("sha256", model.sha256); put("sizeBytes", model.sizeBytes); put("runtime", model.runtime)
        put("inputContract", model.inputContract); put("outputContract", model.outputContract)
        put("capabilities", JSONArray(model.capabilities.toList())); put("minAppVersion", model.minAppVersion)
        put("minMemoryMb", model.minMemoryMb); put("enabled", model.enabled)
    } })
}
