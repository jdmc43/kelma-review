package tech.kelma.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The app's colour scheme. Classic (the default) is a neutral dark grey with blue, red and
 * green counts; Kelma olive is the original near-black olive with gold accents.
 */
enum class KelmaColorScheme(val key: String, val label: String) {
    Classic("classic", "Classic"),
    Olive("olive", "Kelma olive"),
    ;

    companion object {
        val Default = Classic

        fun fromKey(key: String?): KelmaColorScheme = entries.firstOrNull { it.key == key } ?: Default
    }
}

/** The scheme in use. Colours read it, so changing it recolours the app at once. */
object KelmaColorSchemeState {
    var current: KelmaColorScheme by mutableStateOf(KelmaColorScheme.Default)

    // Where a chosen scheme is kept (this device's registry; set by the app).
    internal var save: ((KelmaColorScheme) -> Unit)? = null

    /** The learner's choice (Options): applied at once and kept for next time. */
    fun choose(scheme: KelmaColorScheme) {
        current = scheme
        save?.invoke(scheme)
    }
}

internal data class MobilePalette(
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val surfaceBorder: Color,
    val hairline: Color,
    val gold: Color,
    val goldSoft: Color,
    val goldBright: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val good: Color,
    val bad: Color,
    val newCard: Color,
    val young: Color,
    val hard: Color,
    val easy: Color,
)

internal data class DesktopPalette(
    val background: Color,
    val toolbar: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val gold: Color,
    val new: Color,
    val learn: Color,
    val due: Color,
    val utilityButton: Color,
)

internal val OliveMobilePalette = MobilePalette(
    background = Color(0xFF0F100A),
    backgroundAlt = Color(0xFF141610),
    surface = Color(0xFF1B1D16),
    surfaceElevated = Color(0xFF24271D),
    surfaceHigh = Color(0xFF2D3024),
    surfaceBorder = Color(0xFF3A3D31),
    hairline = Color(0xFF2A2C22),
    gold = Color(0xFFC9AC6B),
    goldSoft = Color(0xFFDCC48F),
    goldBright = Color(0xFFECD49A),
    textPrimary = Color(0xFFF4F1E7),
    textSecondary = Color(0xFFADAEA1),
    textMuted = Color(0xFF7B7D70),
    good = Color(0xFF84B07D),
    bad = Color(0xFFD38975),
    newCard = Color(0xFF7FB2C6),
    young = Color(0xFF9CC593),
    hard = Color(0xFFB8995F),
    easy = Color(0xFF6F9FB0),
)

internal val ClassicMobilePalette = MobilePalette(
    background = Color(0xFF2C2C2C),
    backgroundAlt = Color(0xFF303030),
    surface = Color(0xFF333333),
    surfaceElevated = Color(0xFF3A3A3A),
    surfaceHigh = Color(0xFF444444),
    surfaceBorder = Color(0xFF4D4D4D),
    hairline = Color(0xFF3A3A3A),
    gold = Color(0xFFE4E4E4),
    goldSoft = Color(0xFFEDEDED),
    goldBright = Color(0xFFF7F7F7),
    textPrimary = Color(0xFFFCFCFC),
    textSecondary = Color(0xFFBDBDBD),
    textMuted = Color(0xFF8A8A8A),
    good = Color(0xFF22C55E),
    bad = Color(0xFFF87171),
    newCard = Color(0xFF93C5FD),
    young = Color(0xFF86EFAC),
    hard = Color(0xFFFBBF24),
    easy = Color(0xFF60A5FA),
)

internal val OliveDesktopPalette = DesktopPalette(
    background = Color(0xFF0F100B),
    toolbar = Color(0xFF1B1A12),
    surface = Color(0xFF1C1B13),
    surfaceHigh = Color(0xFF393629),
    border = Color(0xFF403821),
    textPrimary = Color(0xFFF4F1E7),
    textSecondary = Color(0xFFC7C2B4),
    textMuted = Color(0xFF958B70),
    accent = Color(0xFF08A64B),
    gold = Color(0xFFE8CF91),
    new = Color(0xFF86BEF4),
    learn = Color(0xFFFF6B73),
    due = Color(0xFF18C45A),
    utilityButton = Color(0xFF737373),
)

internal val ClassicDesktopPalette = DesktopPalette(
    background = Color(0xFF2C2C2C),
    toolbar = Color(0xFF363636),
    surface = Color(0xFF303030),
    surfaceHigh = Color(0xFF393939),
    border = Color(0xFF404040),
    textPrimary = Color(0xFFFCFCFC),
    textSecondary = Color(0xFFC9C9C9),
    textMuted = Color(0xFF7C7C7C),
    accent = Color(0xFF189747),
    gold = Color(0xFFE4E4E4),
    new = Color(0xFF93C5FD),
    learn = Color(0xFFF87171),
    due = Color(0xFF22C55E),
    utilityButton = Color(0xFF606060),
)

internal fun mobilePalette(scheme: KelmaColorScheme = KelmaColorSchemeState.current): MobilePalette =
    if (scheme == KelmaColorScheme.Olive) OliveMobilePalette else ClassicMobilePalette

internal fun desktopPalette(scheme: KelmaColorScheme = KelmaColorSchemeState.current): DesktopPalette =
    if (scheme == KelmaColorScheme.Olive) OliveDesktopPalette else ClassicDesktopPalette

/** A colour as CSS (#RRGGBB), for card HTML. */
internal fun Color.cssHex(): String {
    fun channel(value: Float) = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
    val rgb = channel(red).shl(16) or channel(green).shl(8) or channel(blue)
    return "#" + rgb.toString(16).padStart(6, '0').uppercase()
}
