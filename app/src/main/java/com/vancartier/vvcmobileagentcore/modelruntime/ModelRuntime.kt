package com.vancartier.vvcmobileagentcore.modelruntime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelRuntime(context: Context) {
    private val store = ModelStore(context.applicationContext.filesDir)
    private val catalogFile = File(store.rootDirectory(), "catalog.json")
    private val localRegistry = LocalModelRegistry(catalogFile)
    private val downloader = ModelDownloader(store)

    suspend fun synchronize(registry: ModelRegistry): ModelSyncResult = withContext(Dispatchers.IO) {
        val manifest = registry.fetch()
        val installed = mutableListOf<ModelDescriptor>()
        val skipped = mutableListOf<ModelDescriptor>()
        val failed = mutableMapOf<String, String>()
        for (descriptor in manifest.models.filter { it.enabled }) {
            try {
                val existing = store.installed(descriptor)
                if (existing != null) {
                    if (com.vancartier.vvcmobileagentcore.security.VvcHashCalculator.calculateFileSha256(existing).equals(descriptor.sha256, true)) {
                        skipped += descriptor
                    } else {
                        existing.delete()
                        downloader.downloadAndInstall(descriptor)
                        installed += descriptor
                    }
                } else {
                    downloader.downloadAndInstall(descriptor)
                    installed += descriptor
                }
                store.activate(descriptor)
            } catch (error: Throwable) { failed[descriptor.id] = error.message ?: error.javaClass.simpleName }
        }
        localRegistry.save(manifest)
        ModelSyncResult(installed, skipped, failed)
    }

    suspend fun rollback(id: String, previousDescriptor: ModelDescriptor): File = withContext(Dispatchers.IO) {
        check(store.installed(previousDescriptor) != null) { "La versión solicitada no está instalada" }
        store.activate(previousDescriptor)
    }

    fun activeArtifacts(): Map<String, File> {
        val manifest = runCatching { kotlinx.coroutines.runBlocking(Dispatchers.IO) { localRegistry.fetch() } }.getOrDefault(ModelManifest(emptyList()))
        return manifest.models.mapNotNull { descriptor ->
            val activeVersion = store.activeVersion(descriptor.id)
            if (activeVersion == descriptor.version) store.installed(descriptor)?.let { descriptor.id.lowercase() to it } else null
        }.toMap()
    }

    fun localRegistry(): LocalModelRegistry = localRegistry
    fun store(): ModelStore = store
}
