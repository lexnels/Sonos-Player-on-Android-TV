package com.sonostv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonostv.sonos.SonosGroup
import com.sonostv.sonos.Track

private val PanelWidth = 280.dp

@Composable
fun SidePanel(
    visible: Boolean,
    header: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(220)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .width(PanelWidth)
                .fillMaxHeight()
                .background(SonosColors.PanelBackground)
                .padding(top = 24.dp, bottom = 18.dp),
        ) {
            Text(
                text = header.uppercase(),
                style = SonosText.PanelHeader,
                modifier = Modifier.padding(start = 18.dp, bottom = 10.dp),
            )
            content()
        }
    }
}

@Composable
fun QueueList(
    tracks: List<Track>,
    currentPosition: Int,
    isPlayingWithoutQueue: Boolean,
    onSelect: (Int) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyPanelMessage(
            if (isPlayingWithoutQueue) {
                "This source keeps its own queue, so Sonos has nothing to show here."
            } else {
                "The queue is empty."
            },
        )
        return
    }

    val currentIndex = (currentPosition - 1).coerceIn(tracks.indices)
    val currentItem = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Open on the track that is playing, so the list reads as "now, then up next".
    LaunchedEffect(currentIndex, tracks.size) {
        listState.scrollToItem(currentIndex)
        runCatching { currentItem.requestFocus() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().focusGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(tracks) { index, track ->
            PanelRow(
                primary = track.title ?: "Unknown track",
                secondary = track.artist,
                artUrl = track.artUrl,
                isCurrent = index == currentIndex,
                onClick = { onSelect(index) },
                modifier = if (index == currentIndex) Modifier.focusRequester(currentItem) else Modifier,
            )
        }
    }
}

@Composable
fun RoomList(groups: List<SonosGroup>, selectedId: String?, onSelect: (SonosGroup) -> Unit) {
    val firstItem = remember { FocusRequester() }

    LaunchedEffect(groups.isNotEmpty()) {
        if (groups.isNotEmpty()) runCatching { firstItem.requestFocus() }
    }

    if (groups.isEmpty()) {
        EmptyPanelMessage("No rooms found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(groups) { index, group ->
            PanelRow(
                primary = group.name,
                secondary = if (group.members.size > 1) {
                    group.members.joinToString(", ") { it.name }
                } else {
                    null
                },
                artUrl = null,
                placeholderIcon = Icons.Rounded.Speaker,
                isCurrent = group.id == selectedId,
                onClick = { onSelect(group) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }
}

@Composable
private fun PanelRow(
    primary: String,
    secondary: String?,
    artUrl: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Rounded.MusicNote,
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) SonosColors.ControlFocusedContent else SonosColors.Primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = primary,
                style = SonosText.ListPrimary.copy(color = contentColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = SonosText.ListSecondary.copy(
                        color = if (focused) {
                            SonosColors.ControlFocusedContent.copy(alpha = 0.62f)
                        } else {
                            SonosColors.Secondary
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isCurrent) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (artUrl != null) Icons.Rounded.GraphicEq else Icons.Rounded.Check,
                contentDescription = "Currently selected",
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyPanelMessage(message: String) {
    Text(
        text = message,
        style = SonosText.ListSecondary,
        modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
    )
}
