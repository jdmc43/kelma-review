package tech.kelma.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Composable
internal actual fun rememberLocalAccountRegistryStorage(): LocalAccountRegistryStorage = remember {
    JvmLocalAccountRegistryStorage(desktopDataDirectory())
}

private class JvmLocalAccountRegistryStorage(directory: File) : LocalAccountRegistryStorage {
    private val file = File(directory, "accounts.json")

    override fun read(): String? = file.takeIf(File::isFile)?.readText()

    override fun write(value: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
        temporary.writeText(value)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            temporary.delete()
        }
    }
}

/**
 * Desktop startup: applies this device's stored colour scheme before any window or card
 * viewer exists, and returns its background for the native window layer.
 */
fun applyStoredDesktopColorScheme(): java.awt.Color {
    val scheme = runCatching {
        LocalAccountRegistry(JvmLocalAccountRegistryStorage(desktopDataDirectory())).colorScheme()
    }.getOrDefault(KelmaColorScheme.Default)
    KelmaColorSchemeState.current = scheme
    val background = desktopPalette(scheme).background
    return java.awt.Color(background.red, background.green, background.blue)
}
