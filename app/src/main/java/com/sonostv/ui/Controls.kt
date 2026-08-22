package com.sonostv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A circular remote-focusable control. Focus is expressed the tvOS way: the button lifts,
 * grows slightly and inverts to a solid white pill.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    idleBackground: Color = SonosColors.ControlIdle,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "buttonScale",
    )

    Box(
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .size(size)
            .scale(scale)
            .shadow(if (focused) 18.dp else 0.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(if (focused) SonosColors.ControlFocused else idleBackground)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) SonosColors.ControlFocusedContent else SonosColors.Primary.copy(
                alpha = if (enabled) 1f else 0.3f,
            ),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The playback progress bar. When it holds focus, left and right on the remote scrub
 * through the track instead of moving focus.
 */
@Composable
fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, tween(300), label = "progress")
    val trackHeight by animateDpAsState(if (focused) 5.dp else 3.dp, tween(180), label = "trackHeight")

    Box(
        modifier = modifier
            .height(16.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        onSeek((positionMs + SEEK_STEP_MS).coerceAtMost(durationMs))
                        true
                    }

                    Key.DirectionLeft -> {
                        onSeek((positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
                        true
                    }

                    else -> false
                }
            }
            .focusable(enabled),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(SonosColors.TrackIdle),
        )
        Box(
            Modifier
                .fillMaxWidth(animatedProgress)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(if (focused) Color.White else SonosColors.Primary.copy(alpha = 0.85f)),
        )
    }
}

/** A slim vertical divider used to visually group the transport and utility controls. */
@Composable
fun ControlDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 1.dp, height = 24.dp)
            .background(Color.White.copy(alpha = 0.14f)),
    )
}

private const val SEEK_STEP_MS = 10_000L
