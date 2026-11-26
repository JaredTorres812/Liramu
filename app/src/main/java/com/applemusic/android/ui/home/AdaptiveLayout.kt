package com.applemusic.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Información del tamaño de ventana actual (en dp) para adaptar la UI a teléfono vertical/horizontal,
 * tablets y modo multiventana. Se recalcula automáticamente al rotar o redimensionar.
 */
@Immutable
data class AdaptiveInfo(val widthDp: Int, val heightDp: Int) {
    /** Ventana más ancha que alta (teléfono girado, tablet en horizontal, etc.). */
    val isLandscape: Boolean get() = widthDp > heightDp

    /** Tablet o ventana grande: el lado más corto mide 600dp o más. */
    val isTablet: Boolean get() = minOf(widthDp, heightDp) >= 600

    /** Ancho "expandido" (≥ 600dp): tablets en vertical, teléfonos en horizontal, plegables abiertos. */
    val isExpandedWidth: Boolean get() = widthDp >= 600

    /** Alto reducido (teléfono en horizontal o ventana partida). */
    val isCompactHeight: Boolean get() = heightDp < 480

    /**
     * El reproductor y otras pantallas usan diseño en dos columnas cuando la ventana es horizontal
     * y hay espacio suficiente a lo ancho.
     */
    val useTwoPane: Boolean get() = isLandscape && widthDp >= 560
}

@Composable
fun rememberAdaptiveInfo(): AdaptiveInfo {
    val configuration = LocalConfiguration.current
    val w = configuration.screenWidthDp
    val h = configuration.screenHeightDp
    return remember(w, h) { AdaptiveInfo(w, h) }
}

/**
 * Alto para cabeceras grandes (portada de álbum, foto de artista). En vertical se mantiene el diseño original;
 * en horizontal se limita para que no ocupe más que la pantalla y deje ver el contenido.
 */
@Composable
fun adaptiveHeroHeight(base: Dp): Dp {
    val info = rememberAdaptiveInfo()
    return if (info.isLandscape) {
        val limit = (info.heightDp * 0.82f).dp
        if (limit < base) limit.coerceAtLeast(220.dp) else base
    } else {
        base
    }
}

/** Ancho máximo para contenido de lectura (listas, letras, cola) en tablets: evita líneas ultra largas. */
val AdaptiveReadableMaxWidth: Dp = 760.dp

/** Ancho máximo del reproductor en diseño de una columna (tablet vertical). */
val AdaptivePlayerMaxWidth: Dp = 620.dp

/** Ancho máximo de la barra flotante inferior (mini reproductor + pestañas) en pantallas anchas. */
val AdaptiveBottomBarMaxWidth: Dp = 640.dp

/**
 * Cabecera con foto / video de perfil para TABLET que nunca recorta el contenido:
 * - Horizontal: el medio se muestra completo a la derecha (con su proporción real) y se funde con el fondo
 *   hacia la izquierda, donde quedan el nombre y los botones (contenido de [overlay]).
 * - Vertical: el medio ocupa todo el ancho con su proporción real (alto limitado al 72% de la pantalla).
 */
@Composable
fun TabletHeroMedia(
    imageUrl: String?,
    videoUrl: String?,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentDescription: String?,
    isVideoActive: Boolean = true,
    onImageSuccess: ((android.graphics.drawable.Drawable) -> Unit)? = null,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.(isLandscape: Boolean) -> Unit
) {
    val info = rememberAdaptiveInfo()
    var imageAspect by androidx.compose.runtime.remember(imageUrl) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var videoAspect by androidx.compose.runtime.remember(videoUrl) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val hasVideo = !videoUrl.isNullOrBlank()
    val aspect = when {
        hasVideo && videoAspect > 0f -> videoAspect
        imageAspect > 0f -> imageAspect
        else -> 1f
    }.coerceIn(0.5f, 2.4f)
    val landscape = info.isLandscape
    val bg = backgroundColor

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(bg)
    ) {
        val heroHeight: Dp = if (hasVideo) {
            if (landscape) {
                (info.heightDp * 0.68f).dp.coerceIn(380.dp, 680.dp)
            } else {
                (maxWidth / aspect).coerceIn(360.dp, (info.heightDp * 0.72f).dp)
            }
        } else if (landscape) {
            (info.heightDp * 0.62f).dp.coerceIn(340.dp, 620.dp)
        } else {
            (maxWidth / aspect).coerceAtMost((info.heightDp * 0.72f).dp)
        }
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            // Si el artista tiene video de perfil, ocupa TODO el ancho completo en la parte superior de la pantalla.
            val mediaModifier = if (landscape && !hasVideo) {
                androidx.compose.ui.Modifier
                    .align(androidx.compose.ui.Alignment.CenterEnd)
                    .fillMaxHeight()
                    .aspectRatio(aspect, matchHeightConstraintsFirst = true)
            } else {
                androidx.compose.ui.Modifier.fillMaxSize()
            }
            androidx.compose.foundation.layout.Box(modifier = mediaModifier) {
                if (!imageUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = contentDescription,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onSuccess = { state ->
                            val d = state.result.drawable
                            if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                imageAspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                            }
                            onImageSuccess?.invoke(d)
                        },
                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    )
                }
                if (hasVideo) {
                    MotionArtworkVideo(
                        videoUrl = videoUrl,
                        isActive = isVideoActive,
                        onVideoAspect = { videoAspect = it },
                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    )
                }
                if (landscape && !hasVideo) {
                    // Borde izquierdo del medio fundido con el fondo (sin cortes duros)
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    0f to bg,
                                    0.22f to bg.copy(alpha = 0f),
                                    1f to bg.copy(alpha = 0f)
                                )
                            )
                    )
                }
            }
            // Degradado inferior para que el contenido de abajo se una con el fondo
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to bg.copy(alpha = 0f),
                            if (landscape) 0.70f to bg.copy(alpha = 0f) else 0.55f to bg.copy(alpha = 0f),
                            0.88f to bg.copy(alpha = 0.80f),
                            1f to bg
                        )
                    )
            )
            overlay(landscape)
        }
    }
}
