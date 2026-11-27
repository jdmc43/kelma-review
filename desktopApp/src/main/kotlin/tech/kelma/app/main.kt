package tech.kelma.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.UIManager

fun main() {
    // The stored colour scheme first: the native layers and the card viewer use its background.
    val windowBackground = applyStoredDesktopColorScheme()
    // Swing interop containers exist one frame before/after Compose content during browser
    // mounting. Make that native window layer match the application instead of AWT white.
    UIManager.put("Panel.background", windowBackground)
    UIManager.put("RootPane.background", windowBackground)
    UIManager.put("Viewport.background", windowBackground)
    UIManager.put("ScrollPane.background", windowBackground)

    // Initialize JavaFX and its WebKit engine before the application window is visible. The first
    // WebView otherwise exposes JavaFX's white bootstrap page while the native engine starts.
    warmUpDesktopRichCardRenderer()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1080.dp, height = 760.dp),
            title = "User 1 - Kelma Review",
        ) {
            // The native window layers follow the colour scheme in use (switching recomposes).
            val schemeBackground = KelmaDesktopColors.Background
            LaunchedEffect(schemeBackground) {
                val background = Color(schemeBackground.red, schemeBackground.green, schemeBackground.blue)
                window.minimumSize = Dimension(900, 620)
                window.background = background
                window.rootPane.background = background
                window.rootPane.isOpaque = true
                window.layeredPane.background = background
                window.layeredPane.isOpaque = true
                window.contentPane.background = background
                (window.contentPane as? JComponent)?.isOpaque = true
            }
            App()
        }
    }
}
