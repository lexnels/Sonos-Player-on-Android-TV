package com.sonostv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sonostv.R

/**
 * A deliberately small palette. The interface is meant to disappear behind the artwork,
 * the way the tvOS now-playing screen does.
 */
object SonosColors {
    val Background = Color(0xFF000000)
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.66f)
    val Tertiary = Color.White.copy(alpha = 0.42f)
    val ControlIdle = Color.White.copy(alpha = 0.07f)

    /** Slightly stronger fill so play/pause reads as the primary control. */
    val ControlEmphasis = Color.White.copy(alpha = 0.17f)
    val ControlFocused = Color.White
    val ControlFocusedContent = Color(0xFF0A0A0C)
    val PanelBackground = Color.Black.copy(alpha = 0.9f)
    val TrackIdle = Color.White.copy(alpha = 0.22f)
}

object SonosText {
    private val family = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
    )

    val Eyebrow = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        color = SonosColors.Tertiary,
    )
    val Title = TextStyle(
        fontFamily = family,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeight = 26.sp,
        color = SonosColors.Primary,
    )
    val Artist = TextStyle(
        fontFamily = family,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.1).sp,
        color = SonosColors.Secondary,
    )
    val Album = TextStyle(
        fontFamily = family,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp,
        color = SonosColors.Tertiary,
    )
    val Timecode = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = SonosColors.Tertiary,
    )
    val PanelHeader = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = SonosColors.Tertiary,
    )
    val ListPrimary = TextStyle(
        fontFamily = family,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = SonosColors.Primary,
    )
    val ListSecondary = TextStyle(
        fontFamily = family,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = SonosColors.Secondary,
    )
}

@Composable
fun SonosTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SonosColors.Background,
            surface = SonosColors.Background,
            primary = SonosColors.Primary,
            onBackground = SonosColors.Primary,
            onSurface = SonosColors.Primary,
        ),
        content = content,
    )
}
