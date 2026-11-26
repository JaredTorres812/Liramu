package com.applemusic.android.playback.cast

import android.content.Context
import com.applemusic.android.BuildConfig
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Configuración del Cast SDK.
 *
 * - Con `CAST_RECEIVER_APP_ID` (apple_music.properties) se usa el receptor propio de Liramu
 *   (`docs/cast/index.html`): reproduce con MusicKit JS y muestra portada animada + letra sincronizada.
 * - Sin él se usa el Default Media Receiver de Google: la TV solo muestra la portada y el audio
 *   sigue en el teléfono (Apple Music usa DRM y no se puede mandar el stream tal cual).
 */
class LiramuCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(null)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(receiverAppId())
            .setStopReceiverApplicationWhenEndingSession(true)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        fun hasCustomReceiver(): Boolean = BuildConfig.CAST_RECEIVER_APP_ID.isNotBlank()

        fun receiverAppId(): String =
            BuildConfig.CAST_RECEIVER_APP_ID.ifBlank { CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID }
    }
}
