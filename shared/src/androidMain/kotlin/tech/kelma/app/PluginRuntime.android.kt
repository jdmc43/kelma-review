package tech.kelma.app

// Plugins are desktop JARs; the mobile apps have none (App() turns them off here).
internal actual fun createPlatformPluginRuntime(
    pluginId: String,
    capabilities: Set<PluginCapability>,
    files: Map<String, ByteArray>,
    entrypoint: String,
): PluginRuntime = throw UnsupportedOperationException("Plugins run in Kelma Review for desktop")
