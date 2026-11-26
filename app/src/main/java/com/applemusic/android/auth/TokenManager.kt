package com.applemusic.android.auth

import android.content.Context
import android.webkit.CookieManager
import com.applemusic.android.BuildConfig
import java.net.URLDecoder

data class AppleMusicCredentials(
    val developerToken: String = "",
    val musicUserToken: String = "",
    val storefront: String = "mx",
    val appleMusicStorefrontId: String = "",
    /** Idioma de la cuenta (ej. es-MX, en-US). Vacío hasta que se lee la cuenta. */
    val language: String = "",
    /** Token web oficial (`AMPWebPlay`) extraído de music.apple.com. */
    val webDeveloperToken: String = "",
    /** Token de sesión web (`media-user-token` / MusicKit web) para `amp-api.music.apple.com`. */
    val webUserToken: String = ""
) {
    val hasDeveloperToken: Boolean get() = developerToken.isNotBlank()
    val hasUserToken: Boolean get() = musicUserToken.isNotBlank()
    val hasWebSession: Boolean get() = webUserToken.isNotBlank()
}

/**
 * Administra credenciales tanto para la API pública (`api.music.apple.com`, .p8)
 * como para el cliente web directo (`amp-api.music.apple.com`).
 */
class TokenManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedDeveloperToken: String? = null

    fun getCredentials(): AppleMusicCredentials {
        val devToken = resolveDeveloperToken()
        val userToken = prefs.getString(KEY_USER_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.APPLE_MUSIC_USER_TOKEN
        val storefront = prefs.getString(KEY_STOREFRONT, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.APPLE_STOREFRONT.ifBlank { "mx" }
        val appleMusicStorefrontId = prefs.getString(KEY_APPLE_MUSIC_STOREFRONT, null).orEmpty()

        return AppleMusicCredentials(
            developerToken = devToken,
            musicUserToken = userToken,
            storefront = storefront,
            appleMusicStorefrontId = appleMusicStorefrontId,
            language = prefs.getString(KEY_LANGUAGE, null).orEmpty(),
            webDeveloperToken = prefs.getString(KEY_WEB_DEV_TOKEN, null).orEmpty(),
            webUserToken = prefs.getString(KEY_WEB_USER_TOKEN, null).orEmpty()
        )
    }

    fun getValidWebDeveloperToken(): String? {
        val token = prefs.getString(KEY_WEB_DEV_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val exp = prefs.getLong(KEY_WEB_DEV_EXP, 0L)
        val nowSec = System.currentTimeMillis() / 1000L
        return if (exp == 0L || exp - nowSec > 3600L) token else null
    }

    fun saveWebDeveloperToken(token: String, expiresAtEpochSec: Long) {
        val clean = token.trim()
        if (clean.isBlank()) return
        val exp = if (expiresAtEpochSec > 0L) expiresAtEpochSec else WebTokenProvider.parseExpirationEpochSec(clean)
        prefs.edit()
            .putString(KEY_WEB_DEV_TOKEN, clean)
            .putLong(KEY_WEB_DEV_EXP, exp)
            .apply()
    }

    fun saveWebSession(webUserToken: String, webDeveloperToken: String? = null) {
        val cleanUser = webUserToken.trim()
        if (cleanUser.isBlank()) return
        prefs.edit().apply {
            putString(KEY_WEB_USER_TOKEN, cleanUser)
            if (!webDeveloperToken.isNullOrBlank()) {
                val cleanDev = webDeveloperToken.trim()
                putString(KEY_WEB_DEV_TOKEN, cleanDev)
                putLong(KEY_WEB_DEV_EXP, WebTokenProvider.parseExpirationEpochSec(cleanDev))
            }
        }.apply()
    }

    fun clearWebUserToken() {
        prefs.edit().remove(KEY_WEB_USER_TOKEN).apply()
    }

    /**
     * Si el usuario ya inició sesión antes en music.apple.com dentro del WebView de la app,
     * recupera la cookie `media-user-token` sin tener que abrir ningún WebView.
     */
    fun syncWebUserTokenFromCookies(): String? {
        val existing = prefs.getString(KEY_WEB_USER_TOKEN, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val rawCookies = runCatching {
            CookieManager.getInstance().getCookie("https://music.apple.com")
        }.getOrNull().orEmpty()
        val match = MEDIA_USER_TOKEN_COOKIE_REGEX.find(rawCookies) ?: return null
        val token = runCatching { URLDecoder.decode(match.groupValues[1].trim(), "UTF-8") }
            .getOrDefault(match.groupValues[1].trim())
            .takeIf { it.isNotBlank() } ?: return null
        saveWebSession(webUserToken = token)
        return token
    }

    fun saveRuntimeCredentials(
        developerToken: String? = null,
        musicUserToken: String? = null,
        storefront: String? = null,
        appleMusicStorefrontId: String? = null
    ) {
        prefs.edit().apply {
            if (developerToken != null) {
                putString(KEY_DEV_TOKEN, developerToken.trim())
                cachedDeveloperToken = developerToken.trim().takeIf { it.isNotBlank() }
            }
            if (musicUserToken != null) {
                putString(KEY_USER_TOKEN, musicUserToken.trim())
            }
            if (storefront != null && storefront.isNotBlank()) {
                putString(KEY_STOREFRONT, storefront.trim().lowercase())
            }
            if (appleMusicStorefrontId != null) {
                putString(KEY_APPLE_MUSIC_STOREFRONT, appleMusicStorefrontId.trim())
            }
        }.apply()
    }

    /** Guarda el país e idioma de la cuenta que inició sesión. */
    fun saveAccountRegion(storefront: String, language: String) {
        prefs.edit()
            .putString(KEY_STOREFRONT, storefront.trim().lowercase())
            .putString(KEY_LANGUAGE, language.trim())
            .apply()
    }

    fun generateAndSaveDeveloperToken(teamId: String, keyId: String, p8Content: String): String {
        val token = AppleMusicJwtGenerator.generateDeveloperToken(
            teamId = teamId.trim(),
            keyId = keyId.trim(),
            p8PrivateKeyPem = p8Content.trim()
        )
        saveRuntimeCredentials(developerToken = token)
        return token
    }

    fun clearRuntimeCredentials() {
        prefs.edit().clear().apply()
        cachedDeveloperToken = null
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    private fun resolveDeveloperToken(): String {
        val runtimeToken = prefs.getString(KEY_DEV_TOKEN, null)?.takeIf { it.isNotBlank() }
        if (runtimeToken != null) return runtimeToken

        if (BuildConfig.APPLE_DEVELOPER_TOKEN.isNotBlank()) {
            return BuildConfig.APPLE_DEVELOPER_TOKEN
        }

        return cachedDeveloperToken.orEmpty()
    }

    private companion object {
        const val PREFS_NAME = "apple_music_tokens"
        const val KEY_DEV_TOKEN = "developer_token"
        const val KEY_USER_TOKEN = "music_user_token"
        const val KEY_STOREFRONT = "storefront"
        const val KEY_APPLE_MUSIC_STOREFRONT = "apple_music_storefront_id"
        const val KEY_LANGUAGE = "account_language"
        const val KEY_WEB_DEV_TOKEN = "web_developer_token"
        const val KEY_WEB_DEV_EXP = "web_developer_token_exp"
        const val KEY_WEB_USER_TOKEN = "web_user_token"

        val MEDIA_USER_TOKEN_COOKIE_REGEX = Regex("""(?:^|;\s*)media-user-token=([^;]+)""")
    }
}
