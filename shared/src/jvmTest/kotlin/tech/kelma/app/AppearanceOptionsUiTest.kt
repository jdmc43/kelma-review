package tech.kelma.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppearanceOptionsUiTest {
    @AfterTest
    fun reset() {
        KelmaColorSchemeState.save = null
        KelmaColorSchemeState.current = KelmaColorScheme.Default
    }

    @Test
    fun optionsSwitchBetweenClassicAndKelmaOlive() = runComposeUiTest {
        val saved = mutableListOf<KelmaColorScheme>()
        KelmaColorSchemeState.save = { saved += it }
        setContent { KelmaTheme { ColorSchemeOption(desktop = true) } }

        onNodeWithTag("color-scheme-classic").assertIsSelected()
        onNodeWithTag("color-scheme-olive").assertIsNotSelected().performClick()
        onNodeWithTag("color-scheme-olive").assertIsSelected()
        assertEquals(KelmaColorScheme.Olive, KelmaColorSchemeState.current)
        onNodeWithTag("color-scheme-classic").performClick()
        assertEquals(KelmaColorScheme.Classic, KelmaColorSchemeState.current)
        assertEquals(listOf(KelmaColorScheme.Olive, KelmaColorScheme.Classic), saved)
    }
}
