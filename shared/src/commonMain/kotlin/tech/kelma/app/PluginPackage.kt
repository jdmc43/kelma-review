package tech.kelma.app

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

internal const val MaximumPluginPackageBytes: Int = 16 * 1024 * 1024
private const val MaximumPluginExpandedBytes: Long = 64L * 1024 * 1024
private const val MaximumPluginEntries: Int = 20_000
private const val MaximumManifestBytes: Long = 64L * 1024

/** A plugin's manifest, at the root of its JAR. */
internal const val PluginManifestFile: String = "kelma-plugin.json"

internal data class PluginPackage(
    val manifest: PluginManifest,
    val files: Map<String, ByteArray>,
)

/**
 * A desktop plugin `.jar`: its `kelma-plugin.json` read and checked, its entrypoint class
 * present. The JAR itself is what gets stored ([PluginJarFile]).
 */
internal fun decodePluginPackage(document: InterchangeDocument, json: Json): PluginPackage {
    require(document.bytes.size <= MaximumPluginPackageBytes) { "Plugin JARs cannot exceed 16 MiB" }
    require(document.bytes.size >= 4 && document.bytes[0] == 'P'.code.toByte() &&
        document.bytes[1] == 'K'.code.toByte()
    ) { "The selected file is not a plugin .jar" }
    val archivePath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kelma-plugin-${randomUuidString()}.jar"
    FileSystem.SYSTEM.sink(archivePath).buffer().use { it.write(document.bytes) }
    try {
        FileSystem.SYSTEM.openZip(archivePath).use { archive ->
            val entries = archive.listRecursively("/".toPath()).take(MaximumPluginEntries + 1).toList()
            require(entries.size <= MaximumPluginEntries) { "Plugin JARs must contain at most $MaximumPluginEntries entries" }
            val expanded = entries.sumOf { path -> archive.metadata(path).takeIf { it.isRegularFile }?.size ?: 0L }
            require(expanded <= MaximumPluginExpandedBytes) { "The plugin JAR expands beyond 64 MiB" }
            val manifestPath = "/$PluginManifestFile".toPath()
            val manifestMetadata = archive.metadataOrNull(manifestPath)
            require(manifestMetadata?.isRegularFile == true) { "The JAR has no $PluginManifestFile, so it isn't a Kelma plugin" }
            require((manifestMetadata?.size ?: 0) in 1..MaximumManifestBytes) { "$PluginManifestFile is empty or too large" }
            val manifestText = archive.source(manifestPath).buffer().use { it.readByteArray() }
                .decodeToString(throwOnInvalidSequence = true)
            val manifest = json.decodeFromString<PluginManifest>(manifestText).validatedForInstall()
            val entryClass = "/" + manifest.entrypoint.replace('.', '/') + ".class"
            require(archive.metadataOrNull(entryClass.toPath())?.isRegularFile == true) {
                "The JAR doesn't contain its entrypoint class ${manifest.entrypoint}"
            }
            return PluginPackage(manifest, mapOf(PluginJarFile to document.bytes))
        }
    } catch (failure: IllegalArgumentException) {
        throw failure
    } catch (failure: Exception) {
        throw IllegalArgumentException("The selected plugin JAR is corrupt", failure)
    } finally {
        FileSystem.SYSTEM.delete(archivePath, mustExist = false)
    }
}
