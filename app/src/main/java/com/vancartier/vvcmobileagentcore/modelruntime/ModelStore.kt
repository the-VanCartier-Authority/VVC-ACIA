package com.vancartier.vvcmobileagentcore.modelruntime

import java.io.File

class ModelStore(filesDir: File) {
    private val root = File(filesDir, "models")
    private val versions = File(root, "versions")
    private val active = File(root, "active")
    private val temp = File(root, "temp")

    init { listOf(root, versions, active, temp).forEach(File::mkdirs) }

    fun temporaryFile(id: String, version: String): File = File(temp, "${safe(id)}-${safe(version)}.partial")
    fun versionFile(descriptor: ModelDescriptor): File = File(File(versions, safe(descriptor.id)), safe(descriptor.version) + ".artifact")
    fun activePointer(id: String): File = File(active, safe(id))

    fun install(descriptor: ModelDescriptor, partial: File): File {
        val destination = versionFile(descriptor)
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        check(partial.renameTo(destination)) { "No se pudo instalar ${descriptor.id}:${descriptor.version}" }
        return destination
    }

    fun activate(descriptor: ModelDescriptor): File {
        val artifact = versionFile(descriptor)
        check(artifact.isFile) { "Versión no instalada: ${descriptor.id}:${descriptor.version}" }
        val pointer = activePointer(descriptor.id)
        val pending = File(pointer.parentFile, pointer.name + ".pending")
        pending.writeText(descriptor.version, Charsets.UTF_8)
        check(pending.renameTo(pointer)) { "No se pudo activar ${descriptor.id}:${descriptor.version}" }
        return artifact
    }

    fun activeVersion(id: String): String? = activePointer(id).takeIf { it.isFile }?.readText()?.trim()?.takeIf(String::isNotEmpty)
    fun installed(descriptor: ModelDescriptor): File? = versionFile(descriptor).takeIf(File::isFile)
    fun removeTemporary(descriptor: ModelDescriptor) { temporaryFile(descriptor.id, descriptor.version).delete() }
    fun rootDirectory(): File = root

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
