package com.sonostv.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sonostv.AppSettings
import com.sonostv.BackgroundStyle
import com.sonostv.UiPrefs
import com.sonostv.sonos.SonosGroup

const val BuyMeACoffeeUrl = "https://buymeacoffee.com/lexnels"

@Composable
fun SettingsPanel(
    groups: List<SonosGroup>,
    prefs: UiPrefs,
    settings: AppSettings,
) {
    val firstItem = remember { FocusRequester() }
    val context = LocalContext.current
    val versionLabel = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName} ($code)"
    }
    val qr = remember { coffeeQrBitmap(size = 512) }
    val defaultOptions = remember(groups) {
        listOf(null to "Last used") + groups.map { it.coordinator.uuid to it.name }
    }
    val defaultIndex = defaultOptions.indexOfFirst { it.first == prefs.defaultGroupUuid }.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        runCatching { firstItem.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .focusGroup()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingStepper(
            label = "UI scale",
            valueText = "${(prefs.uiScale * 100).toInt()}%",
            modifier = Modifier.focusRequester(firstItem),
            onStep = { direction ->
                settings.setUiScale(prefs.uiScale + direction * 0.05f)
            },
        )
        SettingStepper(
            label = "Corner radius",
            valueText = "${prefs.cornerRadiusDp.toInt()} dp",
            onStep = { direction ->
                settings.setCornerRadius(prefs.cornerRadiusDp + direction * 2f)
            },
        )
        SettingStepper(
            label = "Background",
            valueText = when (prefs.backgroundStyle) {
                BackgroundStyle.Ambient -> "Ambient"
                BackgroundStyle.LavaLamp -> "Lava lamp"
            },
            onStep = { direction ->
                val styles = BackgroundStyle.entries
                val index = styles.indexOf(prefs.backgroundStyle).coerceAtLeast(0)
                settings.setBackgroundStyle(styles[(index + direction).mod(styles.size)])
            },
        )
        SettingStepper(
            label = "Default speaker",
            valueText = defaultOptions[defaultIndex].second,
            onStep = { direction ->
                val next = (defaultIndex + direction).mod(defaultOptions.size)
                settings.setDefaultGroupUuid(defaultOptions[next].first)
            },
        )

        Spacer(Modifier.height(10.dp))

        CoffeeQrCard(qr = qr, onOpen = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(BuyMeACoffeeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        })

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Made by LEXNELS",
            style = SonosText.ListSecondary.copy(color = Color.White.copy(alpha = 0.5f)),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = versionLabel,
            style = SonosText.ListSecondary.copy(color = Color.White.copy(alpha = 0.5f)),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SettingStepper(
    label: String,
    valueText: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) SonosColors.ControlFocusedContent else SonosColors.Primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onStep(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        onStep(1)
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onStep(1) },
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = SonosText.ListSecondary.copy(color = if (focused) contentColor.copy(alpha = 0.62f) else SonosColors.Secondary))
            Text(text = valueText, style = SonosText.ListPrimary.copy(color = contentColor))
        }
        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CoffeeQrCard(qr: Bitmap, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Like the app? Buy me a coffee",
            style = SonosText.ListPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = "Buy me a coffee QR code",
            modifier = Modifier.size(148.dp),
        )
        Text(
            text = "buymeacoffee.com/lexnels",
            style = SonosText.ListSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun coffeeQrBitmap(size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        BuyMeACoffeeUrl,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (matrix[x, y]) 0xFFFFFFFF.toInt() else 0x00000000
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
