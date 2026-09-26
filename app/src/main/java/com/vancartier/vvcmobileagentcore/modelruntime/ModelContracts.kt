package com.vancartier.vvcmobileagentcore.modelruntime

import kotlinx.coroutines.flow.Flow

data class ModelDescriptor(
    val id: String,
    val version: String,
    val artifactUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val runtime: String = "litert",
    val inputContract: String = "",
    val outputContract: String = "",
    val capabilities: Set<String> = emptySet(),
    val minAppVersion: String = "1.0.0",
    val minMemoryMb: Int = 0,
    val enabled: Boolean = true
)

data class ModelManifest(val models: List<ModelDescriptor>, val generatedAt: String? = null)

data class ModelProgress(val modelId: String, val downloadedBytes: Long, val totalBytes: Long)

data class ModelSyncResult(val installed: List<ModelDescriptor>, val skipped: List<ModelDescriptor>, val failed: Map<String, String>)

interface ModelRegistry {
    suspend fun fetch(): ModelManifest
}

interface LlmProvider {
    val providerId: String
    val modelId: String
    suspend fun generate(prompt: String, capabilities: Set<String> = emptySet()): Flow<String>
}

class UnsupportedLocalLlmProvider(
    override val modelId: String = "unimplemented"
) : LlmProvider {
    override val providerId: String = "local-litert-lm"
    override suspend fun generate(prompt: String, capabilities: Set<String>): Flow<String> {
        error("LiteRT-LM local todavía no está implementado; no se generan resultados simulados")
    }
}
