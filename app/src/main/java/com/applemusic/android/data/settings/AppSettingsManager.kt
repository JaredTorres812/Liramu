package com.applemusic.android.data.settings

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AppFontOption(val id: String, val label: String, val description: String) {
    SYSTEM("system", "Sistema", "Fuente limpia predeterminada"),
    GEOMETRIC("geometric", "Moderna", "Espaciado amplio y geométrico"),
    SERIF_EDITORIAL("editorial", "Editorial Serif", "Estilo revista musical clásica"),
    STUDIO_MONO("mono", "Studio Mono", "Tipografía técnica de estudio")
}

enum class AppLanguageOption(val tag: String, val label: String) {
    ES("es-MX", "Español"),
    EN("en-US", "English")
}

enum class CacheLimitPreset(val mb: Int, val label: String) {
    MB_128(128, "128 MB"),
    MB_256(256, "256 MB"),
    MB_512(512, "512 MB"),
    GB_1(1024, "1 GB"),
    GB_2(2048, "2 GB")
}

data class UserAccountProfile(
    val id: String = "",
    val name: String = "",
    val handle: String? = null,
    val avatarUrl: String? = null
)

data class DiskCacheStats(
    val artworkBytes: Long = 0L,
    val catalogHttpBytes: Long = 0L,
    val motionVideoBytes: Long = 0L,
    val jsonSnapshotBytes: Long = 0L,
    val otherCacheBytes: Long = 0L
) {
    val totalBytes: Long
        get() = artworkBytes + catalogHttpBytes + motionVideoBytes + jsonSnapshotBytes + otherCacheBytes
}

data class EqualizerUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val presetIndex: Int = 0,
    val presetNames: List<String> = emptyList(),
    val bandLevelsMb: List<Int> = emptyList(),
    val bandCenterHz: List<Int> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bassBoostStrength: Int = 0
)

data class AppSettingsUiState(
    val fontOption: AppFontOption = AppFontOption.SYSTEM,
    val languageOption: AppLanguageOption = AppLanguageOption.ES,
    val cacheLimitMb: Int = 512,
    val cacheStats: DiskCacheStats = DiskCacheStats(),
    val crossfadeSeconds: Int = 0,
    val autoMixEnabled: Boolean = false,
    val equalizer: EqualizerUiState = EqualizerUiState(),
    val userProfile: UserAccountProfile? = null
)

/**
 * Centraliza configuración persistente de Diseño, Audio (Atmos, Ecualizador, Crossfade, AutoMix),
 * límites/uso real de Caché y perfil del usuario.
 */
class AppSettingsManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var eqEngine: Equalizer? = null
    private var bassBoostEngine: BassBoost? = null

    private val _state = MutableStateFlow(readInitialState())
    val state: StateFlow<AppSettingsUiState> = _state.asStateFlow()

    init {
        initEqualizerSafe()
    }

    private fun readInitialState(): AppSettingsUiState {
        val font = prefs.getString(KEY_FONT, AppFontOption.SYSTEM.id)
            ?.let { id -> AppFontOption.entries.firstOrNull { it.id == id } }
            ?: AppFontOption.SYSTEM
        val savedTag = prefs.getString(KEY_LANG, null)
        val defaultLang = if (java.util.Locale.getDefault().language.equals("en", ignoreCase = true)) {
            AppLanguageOption.EN
        } else {
            AppLanguageOption.ES
        }
        val lang = when {
            savedTag == null || savedTag == "auto" -> defaultLang
            savedTag.startsWith("en", ignoreCase = true) -> AppLanguageOption.EN
            else -> AppLanguageOption.ES
        }
        val limitMb = prefs.getInt(KEY_CACHE_LIMIT_MB, 512).coerceIn(128, 2048)
        val crossfadeSec = prefs.getInt(KEY_CROSSFADE_SEC, 0).coerceIn(0, 12)
        val autoMix = prefs.getBoolean(KEY_AUTOMIX, false)
        val profile = readSavedProfile()
        return AppSettingsUiState(
            fontOption = font,
            languageOption = lang,
            cacheLimitMb = limitMb,
            crossfadeSeconds = crossfadeSec,
            autoMixEnabled = autoMix,
            userProfile = profile
        )
    }

    private fun readSavedProfile(): UserAccountProfile? {
        val raw = prefs.getString(KEY_USER_PROFILE_JSON, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            UserAccountProfile(
                id = obj.optString("id"),
                name = obj.optString("name"),
                handle = obj.optString("handle").takeIf { it.isNotBlank() },
                avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    fun saveUserProfile(profile: UserAccountProfile?) {
        if (profile == null) {
            prefs.edit().remove(KEY_USER_PROFILE_JSON).apply()
            _state.update { it.copy(userProfile = null) }
            return
        }
        val json = JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("handle", profile.handle.orEmpty())
            .put("avatarUrl", profile.avatarUrl.orEmpty())
            .toString()
        prefs.edit().putString(KEY_USER_PROFILE_JSON, json).apply()
        _state.update { it.copy(userProfile = profile) }
    }

    fun setFontOption(option: AppFontOption) {
        prefs.edit().putString(KEY_FONT, option.id).apply()
        _state.update { it.copy(fontOption = option) }
    }

    fun setLanguageOption(option: AppLanguageOption) {
        prefs.edit().putString(KEY_LANG, option.tag).apply()
        _state.update { it.copy(languageOption = option) }
        runCatching {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(option.tag))
            val locales = LocaleListCompat.forLanguageTags(option.tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun effectiveLanguageOverride(): String = _state.value.languageOption.tag

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        prefs.edit().putInt(KEY_CROSSFADE_SEC, clamped).apply()
        _state.update { it.copy(crossfadeSeconds = clamped) }
    }

    fun setAutoMixEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOMIX, enabled).apply()
        _state.update { it.copy(autoMixEnabled = enabled) }
    }

    fun setCacheLimitMb(limitMb: Int) {
        val clamped = limitMb.coerceIn(128, 2048)
        prefs.edit().putInt(KEY_CACHE_LIMIT_MB, clamped).apply()
        _state.update { it.copy(cacheLimitMb = clamped) }
    }

    suspend fun refreshCacheStats(): DiskCacheStats = withContext(Dispatchers.IO) {
        val root = appContext.cacheDir
        val coilBytes = dirSizeBytes(File(root, "coil_art_cache")) + dirSizeBytes(File(root, "image_cache"))
        val httpBytes = dirSizeBytes(File(root, "amp_http_cache")) + dirSizeBytes(File(root, "am_http_cache"))
        val motionBytes = dirSizeBytes(File(root, "am_motion_video_cache"))
        val jsonBytes = dirSizeBytes(File(root, "am_json_cache"))
        val known = setOf("coil_art_cache", "image_cache", "amp_http_cache", "am_http_cache", "am_motion_video_cache", "am_json_cache")
        val other = root.listFiles()
            ?.filter { it.name !in known }
            ?.sumOf { if (it.isDirectory) dirSizeBytes(it) else it.length() }
            ?: 0L

        DiskCacheStats(
            artworkBytes = coilBytes,
            catalogHttpBytes = httpBytes,
            motionVideoBytes = motionBytes,
            jsonSnapshotBytes = jsonBytes,
            otherCacheBytes = other
        ).also { stats ->
            _state.update { it.copy(cacheStats = stats) }
        }
    }

    suspend fun trimCachesToLimit(): DiskCacheStats = withContext(Dispatchers.IO) {
        val limitBytes = _state.value.cacheLimitMb.toLong() * 1024L * 1024L
        val current = refreshCacheStats()
        if (current.totalBytes > limitBytes) {
            val trimDirs = listOf(
                File(appContext.cacheDir, "am_motion_video_cache"),
                File(appContext.cacheDir, "coil_art_cache"),
                File(appContext.cacheDir, "amp_http_cache"),
                File(appContext.cacheDir, "am_http_cache"),
                File(appContext.cacheDir, "am_json_cache")
            )
            val allFiles = trimDirs.flatMap { collectFiles(it) }.sortedBy { it.lastModified() }
            var total = current.totalBytes
            for (f in allFiles) {
                if (total <= (limitBytes * 0.88).toLong()) break
                val size = f.length()
                if (f.delete()) total -= size
            }
        }
        refreshCacheStats()
    }

    suspend fun clearAllCaches(): DiskCacheStats = withContext(Dispatchers.IO) {
        listOf(
            "coil_art_cache",
            "image_cache",
            "amp_http_cache",
            "am_http_cache",
            "am_motion_video_cache",
            "am_json_cache"
        ).forEach { name ->
            File(appContext.cacheDir, name).deleteRecursively()
        }
        refreshCacheStats()
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    private fun collectFiles(dir: File): List<File> =
        if (!dir.exists()) emptyList() else dir.walkTopDown().filter { it.isFile }.toList()

    // -----------------------------------------------------------------------------------------
    // Ecualizador real de sistema (android.media.audiofx.Equalizer + BassBoost)
    // -----------------------------------------------------------------------------------------

    private fun initEqualizerSafe() {
        runCatching {
            val eq = Equalizer(0, 0)
            eqEngine = eq
            val numBands = eq.numberOfBands.toInt().coerceAtLeast(0)
            val range = eq.bandLevelRange
            val minMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
            val maxMb = range.getOrElse(1) { 1500.toShort() }.toInt()
            val presets = List(eq.numberOfPresets.toInt().coerceAtLeast(0)) { i ->
                eq.getPresetName(i.toShort()).orEmpty().ifBlank { "Preset ${i + 1}" }
            }
            val centers = List(numBands) { b -> eq.getCenterFreq(b.toShort()) / 1000 }
            val savedEnabled = prefs.getBoolean(KEY_EQ_ENABLED, false)
            val savedPreset = prefs.getInt(KEY_EQ_PRESET, -1)
            val savedBandsRaw = prefs.getString(KEY_EQ_BANDS, null)
            val bands = if (!savedBandsRaw.isNullOrBlank()) {
                val arr = JSONArray(savedBandsRaw)
                List(numBands) { idx ->
                    arr.optInt(idx, 0).coerceIn(minMb, maxMb)
                }
            } else {
                List(numBands) { b -> eq.getBandLevel(b.toShort()).toInt() }
            }
            if (savedPreset in presets.indices && savedBandsRaw.isNullOrBlank()) {
                eq.usePreset(savedPreset.toShort())
            } else {
                bands.forEachIndexed { idx, lvl ->
                    runCatching { eq.setBandLevel(idx.toShort(), lvl.toShort()) }
                }
            }
            eq.enabled = savedEnabled

            val bb = runCatching { BassBoost(0, 0) }.getOrNull()
            bassBoostEngine = bb
            val savedBass = prefs.getInt(KEY_BASS_BOOST, 0).coerceIn(0, 1000)
            if (bb != null) {
                runCatching {
                    bb.setStrength(savedBass.toShort())
                    bb.enabled = savedEnabled && savedBass > 0
                }
            }

            _state.update {
                it.copy(
                    equalizer = EqualizerUiState(
                        available = numBands > 0,
                        enabled = savedEnabled,
                        presetIndex = if (savedPreset >= 0) savedPreset else -1,
                        presetNames = presets,
                        bandLevelsMb = List(numBands) { b -> eq.getBandLevel(b.toShort()).toInt() },
                        bandCenterHz = centers,
                        minLevelMb = minMb,
                        maxLevelMb = maxMb,
                        bassBoostStrength = savedBass
                    )
                )
            }
        }.onFailure { err ->
            Log.w(TAG, "Equalizer no disponible en este dispositivo: ${err.message}")
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        runCatching {
            eqEngine?.enabled = enabled
            bassBoostEngine?.enabled = enabled && _state.value.equalizer.bassBoostStrength > 0
        }
        _state.update { st -> st.copy(equalizer = st.equalizer.copy(enabled = enabled)) }
    }

    fun applyEqualizerPreset(presetIndex: Int) {
        val eq = eqEngine ?: return
        runCatching {
            eq.usePreset(presetIndex.toShort())
            val numBands = eq.numberOfBands.toInt()
            val updatedBands = List(numBands) { b -> eq.getBandLevel(b.toShort()).toInt() }
            saveBandLevels(updatedBands, presetIndex)
            _state.update { st ->
                st.copy(
                    equalizer = st.equalizer.copy(
                        presetIndex = presetIndex,
                        bandLevelsMb = updatedBands
                    )
                )
            }
        }
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) {
        val eq = eqEngine ?: return
        val clamped = levelMb.coerceIn(_state.value.equalizer.minLevelMb, _state.value.equalizer.maxLevelMb)
        runCatching {
            eq.setBandLevel(bandIndex.toShort(), clamped.toShort())
        }
        val updated = _state.value.equalizer.bandLevelsMb.toMutableList()
        if (bandIndex in updated.indices) {
            updated[bandIndex] = clamped
            saveBandLevels(updated, -1)
            _state.update { st ->
                st.copy(
                    equalizer = st.equalizer.copy(
                        presetIndex = -1,
                        bandLevelsMb = updated
                    )
                )
            }
        }
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        prefs.edit().putInt(KEY_BASS_BOOST, clamped).apply()
        runCatching {
            bassBoostEngine?.apply {
                setStrength(clamped.toShort())
                enabled = _state.value.equalizer.enabled && clamped > 0
            }
        }
        _state.update { st -> st.copy(equalizer = st.equalizer.copy(bassBoostStrength = clamped)) }
    }

    private fun saveBandLevels(levels: List<Int>, presetIndex: Int) {
        val arr = JSONArray()
        levels.forEach { arr.put(it) }
        prefs.edit()
            .putInt(KEY_EQ_PRESET, presetIndex)
            .putString(KEY_EQ_BANDS, arr.toString())
            .apply()
    }

    fun release() {
        runCatching { eqEngine?.release() }
        runCatching { bassBoostEngine?.release() }
        eqEngine = null
        bassBoostEngine = null
    }

    private companion object {
        const val TAG = "AppSettings"
        const val PREFS_NAME = "am_app_settings"
        const val KEY_FONT = "design_font"
        const val KEY_LANG = "design_language"
        const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
        const val KEY_CROSSFADE_SEC = "audio_crossfade_sec"
        const val KEY_AUTOMIX = "audio_automix"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_EQ_BANDS = "eq_bands_json"
        const val KEY_BASS_BOOST = "eq_bass_boost"
        const val KEY_USER_PROFILE_JSON = "user_profile_json"
    }
}
