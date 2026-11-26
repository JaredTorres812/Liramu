package com.applemusic.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.applemusic.android.playback.cast.CastDeviceUi
import com.applemusic.android.playback.cast.CastPlaybackMode
import com.applemusic.android.playback.cast.LiramuCastManager

/**
 * Lista de Chromecast / Google TV cercanos dentro de la hoja "Conectar dispositivo".
 * Busca dispositivos solo mientras la hoja está abierta (ahorra batería).
 */
@Composable
internal fun CastDevicesSection(
    cardBg: Color,
    titleTint: Color,
    modifier: Modifier = Modifier
) {
    val castState by LiramuCastManager.state.collectAsState()
    if (!castState.available) return

    DisposableEffect(Unit) {
        LiramuCastManager.startDiscovery()
        onDispose { LiramuCastManager.stopDiscovery() }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Chromecast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        val hint = when (castState.mode) {
            CastPlaybackMode.TV_AUDIO -> "La TV reproduce el audio con portada y letra en seguimiento."
            CastPlaybackMode.DISPLAY_ONLY -> if (castState.customReceiver) {
                "La TV muestra portada y letra; el audio sigue en este teléfono."
            } else {
                "La TV muestra la portada; el audio sigue en este teléfono."
            }
            CastPlaybackMode.CONNECTING -> "Conectando…"
            CastPlaybackMode.NONE -> if (castState.devices.isEmpty()) {
                "Buscando dispositivos en tu red Wi‑Fi…"
            } else {
                "Toca un dispositivo para ver la portada y la letra en la TV."
            }
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f)
        )

        castState.devices.forEach { device ->
            CastDeviceRow(
                device = device,
                cardBg = cardBg,
                titleTint = titleTint,
                onClick = {
                    if (device.isSelected) LiramuCastManager.disconnect()
                    else LiramuCastManager.selectDevice(device.id)
                }
            )
        }
    }
}

@Composable
private fun CastDeviceRow(
    device: CastDeviceUi,
    cardBg: Color,
    titleTint: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = if (device.isSelected) cardBg else Color(0xFF232520),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (device.isSelected) lerp(cardBg, Color.White, 0.16f) else Color(0xFF2F322B),
                contentColor = Color.White,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (device.isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (device.isSelected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (device.isSelected) titleTint else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = when {
                    device.isConnecting -> "Conectando…"
                    device.isSelected -> "Conectado · toca para desconectar"
                    else -> device.description
                }
                if (!sub.isNullOrBlank()) {
                    if (device.isSelected) {
                        Surface(shape = CircleShape, color = lerp(cardBg, Color.White, 0.14f)) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
