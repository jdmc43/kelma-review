package tech.kelma.app

import java.io.File
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCardSurfaceTest {
    @AfterTest
    fun reset() {
        KelmaColorSchemeState.current = KelmaColorScheme.Default
    }

    @Test
    fun theCardCanvasFollowsTheColorScheme() {
        val classic = DesktopCardSurface.current()
        assertEquals("#2C2C2C", classic.css)
        assertEquals("#FCFCFC", classic.textCss)
        assertTrue(classic.webViewStyle.contains("-fx-page-fill: #2C2C2C"))
        val css = Base64.getDecoder().decode(classic.userStyleSheet.substringAfter("base64,")).decodeToString()
        assertEquals("html,body{background-color:#2C2C2C!important;color:#FCFCFC!important;}", css)

        KelmaColorSchemeState.current = KelmaColorScheme.Olive
        val olive = DesktopCardSurface.current()
        assertEquals("#0F100B", olive.css)
        assertEquals(15, olive.awt.red)
    }

    @Test
    fun noFixedOliveRemainsInTheDesktopCardViewerOrWindow() {
        val sources = listOf(
            File("src/jvmMain/kotlin/tech/kelma/app/RichCardRendering.jvm.kt"),
            File("../desktopApp/src/main/kotlin/tech/kelma/app/main.kt"),
        )
        sources.forEach { source ->
            val text = source.readText()
            assertFalse(Regex("0F100[AB]|Color\\(15, *16, *1[01]\\)|rgb\\(15, *16").containsMatchIn(text), source.path)
        }
    }
}
