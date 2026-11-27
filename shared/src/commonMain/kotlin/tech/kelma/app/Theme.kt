package tech.kelma.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The colours of the scheme in use (KelmaColorSchemeState): reading them in a composable
// recomposes it when the scheme changes.
object KelmaColors {
    val Background: Color get() = mobilePalette().background
    val BackgroundAlt: Color get() = mobilePalette().backgroundAlt
    val Surface: Color get() = mobilePalette().surface
    val SurfaceElevated: Color get() = mobilePalette().surfaceElevated
    val SurfaceHigh: Color get() = mobilePalette().surfaceHigh
    val SurfaceBorder: Color get() = mobilePalette().surfaceBorder
    val Hairline: Color get() = mobilePalette().hairline
    val Gold: Color get() = mobilePalette().gold
    val GoldSoft: Color get() = mobilePalette().goldSoft
    val GoldBright: Color get() = mobilePalette().goldBright
    val TextPrimary: Color get() = mobilePalette().textPrimary
    val TextSecondary: Color get() = mobilePalette().textSecondary
    val TextMuted: Color get() = mobilePalette().textMuted
    val Good: Color get() = mobilePalette().good
    val Bad: Color get() = mobilePalette().bad
    val NewCard: Color get() = mobilePalette().newCard
    val Young: Color get() = mobilePalette().young
    val Hard: Color get() = mobilePalette().hard
    val Easy: Color get() = mobilePalette().easy
}

object KelmaDesktopColors {
    val Background: Color get() = desktopPalette().background
    val Toolbar: Color get() = desktopPalette().toolbar
    val Surface: Color get() = desktopPalette().surface
    val SurfaceHigh: Color get() = desktopPalette().surfaceHigh
    val Border: Color get() = desktopPalette().border
    val TextPrimary: Color get() = desktopPalette().textPrimary
    val TextSecondary: Color get() = desktopPalette().textSecondary
    val TextMuted: Color get() = desktopPalette().textMuted
    val Accent: Color get() = desktopPalette().accent
    val Gold: Color get() = desktopPalette().gold
    val New: Color get() = desktopPalette().new
    val Learn: Color get() = desktopPalette().learn
    val Due: Color get() = desktopPalette().due
    val UtilityButton: Color get() = desktopPalette().utilityButton
}

private fun mobileColorScheme(): ColorScheme = darkColorScheme(
    primary = KelmaColors.Gold,
    onPrimary = KelmaColors.Background,
    primaryContainer = KelmaColors.SurfaceElevated,
    onPrimaryContainer = KelmaColors.GoldSoft,
    secondary = KelmaColors.GoldSoft,
    onSecondary = KelmaColors.Background,
    secondaryContainer = KelmaColors.SurfaceHigh,
    onSecondaryContainer = KelmaColors.TextPrimary,
    tertiary = KelmaColors.Good,
    onTertiary = KelmaColors.Background,
    tertiaryContainer = KelmaColors.SurfaceElevated,
    onTertiaryContainer = KelmaColors.Good,
    background = KelmaColors.Background,
    onBackground = KelmaColors.TextPrimary,
    surface = KelmaColors.Background,
    onSurface = KelmaColors.TextPrimary,
    surfaceVariant = KelmaColors.Surface,
    onSurfaceVariant = KelmaColors.TextSecondary,
    outline = KelmaColors.SurfaceBorder,
    outlineVariant = KelmaColors.Hairline,
    error = KelmaColors.Bad,
    onError = KelmaColors.Background,
    errorContainer = KelmaColors.SurfaceElevated,
    onErrorContainer = KelmaColors.Bad,
)

private fun desktopColorScheme(): ColorScheme = darkColorScheme(
    primary = KelmaDesktopColors.Gold,
    onPrimary = KelmaDesktopColors.Background,
    primaryContainer = KelmaDesktopColors.SurfaceHigh,
    onPrimaryContainer = KelmaDesktopColors.TextPrimary,
    secondary = KelmaDesktopColors.TextSecondary,
    onSecondary = KelmaDesktopColors.Background,
    tertiary = KelmaDesktopColors.Accent,
    onTertiary = KelmaDesktopColors.Background,
    background = KelmaDesktopColors.Background,
    onBackground = KelmaDesktopColors.TextPrimary,
    surface = KelmaDesktopColors.Background,
    onSurface = KelmaDesktopColors.TextPrimary,
    surfaceVariant = KelmaDesktopColors.Surface,
    onSurfaceVariant = KelmaDesktopColors.TextSecondary,
    outline = KelmaDesktopColors.Border,
    outlineVariant = KelmaDesktopColors.Border,
    error = KelmaColors.Bad,
    onError = KelmaDesktopColors.Background,
)

private val MobileShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

private val DesktopShapes = Shapes(
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(7.dp),
    large = RoundedCornerShape(10.dp),
)

private val MobileTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)
private val DesktopTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun KelmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = remember(KelmaColorSchemeState.current) {
            if (isDesktopApp) desktopColorScheme() else mobileColorScheme()
        },
        shapes = if (isDesktopApp) DesktopShapes else MobileShapes,
        typography = if (isDesktopApp) DesktopTypography else MobileTypography,
        content = content,
    )
}
