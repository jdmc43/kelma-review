package tech.kelma.app

import androidx.compose.ui.graphics.Color
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KelmaColorSchemeTest {
    @AfterTest
    fun reset() {
        KelmaColorSchemeState.save = null
        KelmaColorSchemeState.current = KelmaColorScheme.Default
    }

    @Test
    fun classicIsTheDefaultAndUnknownKeysFallBackToIt() {
        assertEquals(KelmaColorScheme.Classic, KelmaColorScheme.Default)
        assertEquals(KelmaColorScheme.Olive, KelmaColorScheme.fromKey("olive"))
        assertEquals(KelmaColorScheme.Classic, KelmaColorScheme.fromKey("classic"))
        assertEquals(KelmaColorScheme.Classic, KelmaColorScheme.fromKey(null))
        assertEquals(KelmaColorScheme.Classic, KelmaColorScheme.fromKey("pink"))
    }

    @Test
    fun choosingASchemeRecoloursAtOnceAndKeepsTheChoice() {
        val saved = mutableListOf<KelmaColorScheme>()
        KelmaColorSchemeState.save = { saved += it }
        assertEquals(Color(0xFF2C2C2C), KelmaDesktopColors.Background)
        assertEquals(Color(0xFF93C5FD), KelmaDesktopColors.New)

        KelmaColorSchemeState.choose(KelmaColorScheme.Olive)
        assertEquals(Color(0xFF0F100B), KelmaDesktopColors.Background)
        assertEquals(Color(0xFFC9AC6B), KelmaColors.Gold)

        KelmaColorSchemeState.choose(KelmaColorScheme.Classic)
        assertEquals(Color(0xFF303030), KelmaDesktopColors.Surface)
        assertEquals(listOf(KelmaColorScheme.Olive, KelmaColorScheme.Classic), saved)
    }

    @Test
    fun cardHtmlColoursAreThePalettesAsCss() {
        assertEquals("#2C2C2C", Color(0xFF2C2C2C).cssHex())
        assertEquals("#C9AC6B", Color(0xFFC9AC6B).cssHex())
        assertEquals("#FCFCFC", ClassicDesktopPalette.textPrimary.cssHex())
    }
}
