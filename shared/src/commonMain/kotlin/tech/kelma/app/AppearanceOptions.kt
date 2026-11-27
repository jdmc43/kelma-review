package tech.kelma.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Options' Appearance: the colour scheme, Classic (the default) or Kelma olive. It applies
 * at once and is kept on this device (not with the deck's options).
 */
@Composable
internal fun ColorSchemeOption(desktop: Boolean) {
    val current = KelmaColorSchemeState.current
    val secondary = if (desktop) KelmaDesktopColors.TextSecondary else KelmaColors.TextSecondary
    val muted = if (desktop) KelmaDesktopColors.TextMuted else KelmaColors.TextMuted
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Color scheme", color = secondary, fontSize = if (desktop) 13.sp else 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KelmaColorScheme.entries.forEach { scheme ->
                ColorSchemeChoice(scheme, selected = scheme == current, desktop = desktop) {
                    KelmaColorSchemeState.choose(scheme)
                }
            }
        }
        Text("Applies at once on this device.", color = muted, fontSize = if (desktop) 11.sp else 13.sp)
    }
}

@Composable
private fun ColorSchemeChoice(scheme: KelmaColorScheme, selected: Boolean, desktop: Boolean, onSelect: () -> Unit) {
    val surface = if (desktop) KelmaDesktopColors.SurfaceHigh else KelmaColors.SurfaceElevated
    val border = when {
        selected -> if (desktop) KelmaDesktopColors.Gold else KelmaColors.Gold
        desktop -> KelmaDesktopColors.Border
        else -> KelmaColors.SurfaceBorder
    }
    val text = if (desktop) KelmaDesktopColors.TextPrimary else KelmaColors.TextPrimary
    Surface(
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag("color-scheme-${scheme.key}"),
        color = surface,
        shape = RoundedCornerShape(if (desktop) 8.dp else 12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (desktop) 8.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SchemeSwatch(scheme)
            Text(
                scheme.label,
                color = text,
                fontSize = if (desktop) 13.sp else 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

// A small preview of the scheme: its canvas with its accent and due colours.
@Composable
private fun SchemeSwatch(scheme: KelmaColorScheme) {
    val palette = desktopPalette(scheme)
    Box(
        modifier = Modifier.size(28.dp, 18.dp).background(palette.background, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Dot(palette.gold)
            Dot(palette.due)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}
