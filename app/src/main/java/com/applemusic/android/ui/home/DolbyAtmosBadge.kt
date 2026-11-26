package com.applemusic.android.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Símbolo de doble D (estilo Dolby): dos "D" espalda con espalda, con los lados planos hacia el centro.
 * Se dibuja con vectores para no depender de ningún recurso externo.
 */
@Composable
fun DolbyDoubleDMark(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    tint: Color = Color.White
) {
    val dWidthRatio = 0.72f
    val gapRatio = 0.14f
    val totalWidth = height * (2f * dWidthRatio + gapRatio)
    Canvas(modifier = modifier.width(totalWidth).height(height)) {
        val h = size.height
        val w = h * dWidthRatio
        val gap = h * gapRatio
        val r = h / 2f

        // D izquierda (espejada): lado plano a la derecha, curva a la izquierda.
        val left = Path().apply {
            moveTo(w, 0f)
            lineTo(r, 0f)
            arcTo(Rect(0f, 0f, h, h), -90f, -180f, false)
            lineTo(w, h)
            close()
        }
        // D derecha: lado plano a la izquierda, curva a la derecha.
        val x0 = w + gap
        val right = Path().apply {
            moveTo(x0, 0f)
            lineTo(x0 + w - r, 0f)
            arcTo(Rect(x0 + w - h, 0f, x0 + w, h), -90f, 180f, false)
            lineTo(x0, h)
            close()
        }
        drawPath(left, tint)
        drawPath(right, tint)
    }
}

/** Insignia "Dolby Atmos": doble D + texto, en píldora translúcida legible sobre cualquier color. */
@Composable
fun DolbyAtmosBadge(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DolbyDoubleDMark(height = 12.dp, tint = tint)
        Text(
            text = "Dolby Atmos",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = tint,
            maxLines = 1
        )
    }
}
