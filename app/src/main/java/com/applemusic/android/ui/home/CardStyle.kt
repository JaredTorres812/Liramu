package com.applemusic.android.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Medidas compartidas para que todas las tarjetas destacadas (Sugerencias, Replay, etc.)
 * tengan el mismo tamaño, con la proporción 3:4 que usa el arte editorial de Apple Music.
 */
internal val FeatureCardWidth = 228.dp
internal val FeatureCardHeight = 304.dp

/** Esquinas generosas estilo Material 3 Expressive para tarjetas editoriales. */
internal val FeatureCardShape = RoundedCornerShape(22.dp)

/** Esquinas extra amplias para banners destacados de Novedades y Radio. */
internal val BannerCardShape = RoundedCornerShape(26.dp)

/** Contenedor expresivo para listas de canciones (Canciones destacadas, del momento, etc.). */
internal val ListCardShape = RoundedCornerShape(24.dp)

/** Portadas de álbum, playlist y canción con redondez expresiva. */
internal val CoverShape = RoundedCornerShape(14.dp)

internal val AppleRed = Color(0xFFFA243C)

/**
 * Blur progresivo superior sutil (estilo expo-backdrop) concentrado en la zona de la barra de estado,
 * arriba de las píldoras flotantes.
 */
@Composable
fun TopProgressiveBlur(
    hazeState: HazeState,
    height: Dp,
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(state = hazeState) {
                blurRadius = 14.dp
                noiseFactor = 0f
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0.85f,
                    endIntensity = 0f
                )
            }
            .background(
                Brush.verticalGradient(
                    0f to tintColor.copy(alpha = 0.48f),
                    0.55f to tintColor.copy(alpha = 0.14f),
                    1f to Color.Transparent
                )
            )
    )
}

/**
 * Blur progresivo inferior (desde la mitad de la barra flotante hacia abajo) estilo expo-backdrop.
 */
@Composable
fun BottomProgressiveBlur(
    hazeState: HazeState,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(state = hazeState) {
                blurRadius = 24.dp
                noiseFactor = 0f
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0f,
                    endIntensity = 1f
                )
            }
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to bgColor.copy(alpha = 0.28f),
                    1f to bgColor.copy(alpha = 0.72f)
                )
            )
    )
}

/**
 * Extrae el color más llamativo / dominante directamente de la foto cargada,
 * priorizando saturación y tonos visibles para que fotos rojas, azules o vibrantes
 * nunca se pinten de negro.
 *
 * - Lee bien los bitmaps de hardware de Coil (antes lanzaban excepción y se perdía el color extraído).
 * - El tono es circular: el rojo (que cae en 350°–10°) se suma como un solo grupo en vez de partirse en dos.
 * - [focusSubject]: pondera más el centro de la foto (el artista) que los bordes/fondo, sin sesgo hacia abajo.
 */
internal fun extractVibrantDominantColor(drawable: Drawable, focusSubject: Boolean = false): Color? = runCatching {
    val size = 32
    val bmp: Bitmap = run {
        val src = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (src != null && src.width > 0 && src.height > 0) {
            val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
            val scaled = Bitmap.createScaledBitmap(soft, size, size, true)
            if (soft !== src && soft !== scaled) soft.recycle()
            // Nunca devolver el bitmap original (se recicla al final y lo usa Coil).
            if (scaled === src) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
        } else {
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val oldBounds = android.graphics.Rect(drawable.bounds)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            drawable.bounds = oldBounds
            out
        }
    }

    val bins = 24
    val weights = FloatArray(bins)
    val sumR = FloatArray(bins)
    val sumG = FloatArray(bins)
    val sumB = FloatArray(bins)
    val hsv = FloatArray(3)

    var fallbackR = 0f
    var fallbackG = 0f
    var fallbackB = 0f
    var fallbackW = 0f

    for (y in 0 until size) {
        for (x in 0 until size) {
            val pixel = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 200) continue
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            val position = if (focusSubject) {
                val dx = (x - 15.5f) / 16f
                val dy = (y - 10f) / 16f
                0.35f + 0.65f * kotlin.math.exp(-(dx * dx / 0.5f + dy * dy / 0.6f))
            } else {
                if (y >= size / 2) 1.35f else 1.0f
            }

            if (sat >= 0.20f && value >= 0.14f && value <= 0.96f) {
                val bin = ((hue / 360f) * bins).toInt().coerceIn(0, bins - 1)
                val w = (sat * sat) * (0.45f + 0.55f * value) * position
                weights[bin] += w
                sumR[bin] += r * w
                sumG[bin] += g * w
                sumB[bin] += b * w
            } else if (value in 0.12f..0.85f) {
                fallbackR += r * position
                fallbackG += g * position
                fallbackB += b * position
                fallbackW += position
            }
        }
    }
    bmp.recycle()

    // Suavizado circular: cada tono suma la mitad de sus vecinos (incluye el salto 360° -> 0°).
    var bestBin = -1
    var bestWeight = 0f
    for (i in 0 until bins) {
        val prev = (i + bins - 1) % bins
        val next = (i + 1) % bins
        val smoothed = weights[i] + 0.5f * (weights[prev] + weights[next])
        if (smoothed > bestWeight) {
            bestWeight = smoothed
            bestBin = i
        }
    }

    when {
        bestBin >= 0 && bestWeight > 1.5f -> {
            val prev = (bestBin + bins - 1) % bins
            val next = (bestBin + 1) % bins
            val total = weights[bestBin] + weights[prev] + weights[next]
            val r = ((sumR[bestBin] + sumR[prev] + sumR[next]) / total).toInt().coerceIn(0, 255)
            val g = ((sumG[bestBin] + sumG[prev] + sumG[next]) / total).toInt().coerceIn(0, 255)
            val b = ((sumB[bestBin] + sumB[prev] + sumB[next]) / total).toInt().coerceIn(0, 255)
            Color(red = r, green = g, blue = b)
        }
        fallbackW > 0f -> {
            val r = (fallbackR / fallbackW).toInt().coerceIn(0, 255)
            val g = (fallbackG / fallbackW).toInt().coerceIn(0, 255)
            val b = (fallbackB / fallbackW).toInt().coerceIn(0, 255)
            Color(red = r, green = g, blue = b)
        }
        else -> null
    }
}.getOrNull()
