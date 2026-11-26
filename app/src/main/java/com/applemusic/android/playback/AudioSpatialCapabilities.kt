package com.applemusic.android.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.Spatializer
import android.os.Build
import android.util.Log
import com.apple.android.music.playback.model.AudioQuality
import com.apple.android.music.playback.model.DolbyAtmosState
import com.apple.android.music.playback.player.AudioConstants
import com.apple.android.music.playback.preferences.MediaPlaybackPreferences
import com.apple.android.music.util.AudioDeviceCapabilities

enum class AtmosPreferenceMode(val label: String) {
    AUTOMATIC("Automático"),
    ALWAYS_ON("Siempre"),
    OFF("No")
}

data class DeviceDolbyCapabilities(
    val hasEac3JocDecoder: Boolean = false,
    val sdkReportsDolbyPlus: Boolean = false,
    val hasSpatializer: Boolean = false,
    val isSpatializerEnabled: Boolean = false,
    val hasSpatialOutputDevice: Boolean = false,
    val summaryLabel: String = "Verificando hardware…"
) {
    val isDolbyAtmosSupported: Boolean
        get() = hasEac3JocDecoder || sdkReportsDolbyPlus || isSpatializerEnabled || hasSpatialOutputDevice
}

object AudioSpatialCapabilities {
    private const val TAG = "AudioSpatialCaps"

    fun inspectDeviceCapabilities(context: Context): DeviceDolbyCapabilities {
        val sdkSupportsDdp = runCatching {
            AudioDeviceCapabilities.isDolbyDigitalPlusAvailable()
        }.getOrDefault(false)

        val hasEac3JocCodec = runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { mime ->
                    mime.equals(MediaFormat.MIMETYPE_AUDIO_EAC3_JOC, ignoreCase = true) ||
                        mime.equals("audio/eac3-joc", ignoreCase = true)
                }
            }
        }.getOrDefault(false)

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        var hasSpatializer = false
        var isSpatializerEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && am != null) {
            runCatching {
                val sp = am.spatializer
                hasSpatializer = sp.isAvailable &&
                    sp.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
                isSpatializerEnabled = hasSpatializer && sp.isEnabled
            }
        }

        val hasSpatialOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && am != null) {
            runCatching {
                val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                outputs.any { dev ->
                    val encs = dev.encodings
                    encs.any { enc ->
                        enc == AudioFormat.ENCODING_E_AC3_JOC ||
                            enc == AudioFormat.ENCODING_E_AC3 ||
                            enc == AudioFormat.ENCODING_DOLBY_TRUEHD ||
                            enc == AudioFormat.ENCODING_AC4
                    } || (isSpatializerEnabled && (
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        ))
                }
            }.getOrDefault(false)
        } else {
            false
        }

        val supported = hasEac3JocCodec || sdkSupportsDdp || isSpatializerEnabled || hasSpatialOutput
        val summary = when {
            (hasEac3JocCodec || sdkSupportsDdp) && isSpatializerEnabled ->
                "Compatible: Decodificador E-AC-3 JOC + Audio Espacial activos"
            hasEac3JocCodec || sdkSupportsDdp ->
                "Compatible: Decodificador Dolby Digital Plus / Atmos (E-AC-3 JOC)"
            isSpatializerEnabled ->
                "Compatible: Audio Espacial Android activo"
            else ->
                "Salida estéreo estándar detectada"
        }

        return DeviceDolbyCapabilities(
            hasEac3JocDecoder = hasEac3JocCodec,
            sdkReportsDolbyPlus = sdkSupportsDdp,
            hasSpatializer = hasSpatializer,
            isSpatializerEnabled = isSpatializerEnabled,
            hasSpatialOutputDevice = hasSpatialOutput,
            summaryLabel = summary
        ).also {
            Log.d(
                TAG,
                "Dolby caps: supported=$supported eac3joc=$hasEac3JocCodec sdkDdp=$sdkSupportsDdp spatial=$hasSpatializer/$isSpatializerEnabled out=$hasSpatialOutput"
            )
        }
    }

    /**
     * Configura `MediaPlaybackPreferences` del SDK oficial de Apple (`MediaPlayback-release-1.1.4.aar`)
     * para reproducir Dolby Atmos (`E-AC-3 JOC`). Lossless queda desactivado a propósito.
     */
    fun applyNativePreferences(
        context: Context,
        atmosMode: AtmosPreferenceMode,
        caps: DeviceDolbyCapabilities = inspectDeviceCapabilities(context)
    ) {
        runCatching {
            val prefs = MediaPlaybackPreferences.with(context.applicationContext)
            val sdkAtmos = when (atmosMode) {
                AtmosPreferenceMode.AUTOMATIC ->
                    if (caps.isDolbyAtmosSupported) DolbyAtmosState.AUTOMATIC else DolbyAtmosState.OFF
                AtmosPreferenceMode.ALWAYS_ON -> DolbyAtmosState.ALWAYS_ON
                AtmosPreferenceMode.OFF -> DolbyAtmosState.OFF
            }
            val atmosActive = sdkAtmos != DolbyAtmosState.OFF
            // Sin Lossless: la calidad pedida es Dolby Atmos (si está activo) o AAC alta calidad.
            val quality = if (atmosActive) AudioQuality.DOLBY_ATMOS else AudioQuality.HIGH_QUALITY
            prefs.setDolbyAtmosState(sdkAtmos)
            prefs.setLosslessEnabled(false)
            prefs.setHighQualityOnCellularEnabled(true)
            prefs.setWifiAudioQuality(quality)
            prefs.setCellularAudioQuality(quality)
            Log.d(
                TAG,
                "Applied Apple SDK prefs: dolby=${prefs.dolbyAtmosState}, wifi=${prefs.wifiAudioQuality}, cellular=${prefs.cellularAudioQuality}"
            )
        }.onFailure {
            Log.w(TAG, "No se pudo configurar MediaPlaybackPreferences", it)
        }
    }

    fun mapAudioVariantBadge(variant: Int): String? = when (variant) {
        AudioConstants.TRACK_VARIANTS_DOLBY_ATMOS -> "Dolby Atmos"
        AudioConstants.TRACK_VARIANTS_AAC_STEREO_HIGH_QUALITY -> "AAC Alta calidad"
        else -> null
    }
}
