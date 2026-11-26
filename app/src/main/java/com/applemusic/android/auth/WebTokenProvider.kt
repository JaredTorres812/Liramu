package com.applemusic.android.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

data class WebTokenResult(
    val token: String,
    val expiresAtEpochSec: Long
)

/**
 * Obtiene el Developer Token web oficial (`AMPWebPlay`) directamente desde music.apple.com,
 * sin usar una llave .p8 de terceros.
 */
class WebTokenProvider(
    private val tokenManager: TokenManager,
    private val client: OkHttpClient = defaultHttpClient()
) {
    private val mutex = Mutex()

    suspend fun getOrFetchWebToken(forceRefresh: Boolean = false): String? = mutex.withLock {
        if (!forceRefresh) {
            val cached = tokenManager.getValidWebDeveloperToken()
            if (cached != null) return@withLock cached
        }
        val fetched = fetchFromAppleMusicWeb() ?: return@withLock null
        tokenManager.saveWebDeveloperToken(fetched.token, fetched.expiresAtEpochSec)
        fetched.token
    }

    private suspend fun fetchFromAppleMusicWeb(): WebTokenResult? = withContext(Dispatchers.IO) {
        val html = fetchText("$APPLE_MUSIC_WEB/us/home") ?: return@withContext null
        val bundlePaths = extractBundlePaths(html)
        for (path in bundlePaths) {
            val url = if (path.startsWith("http")) path else "$APPLE_MUSIC_WEB$path"
            val js = fetchText(url) ?: continue
            val extracted = extractWebTokenFromJs(js)
            if (extracted != null) return@withContext extracted
        }
        null
    }

    private fun fetchText(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept", "text/html,application/javascript,*/*")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }.getOrNull()

    companion object {
        const val APPLE_MUSIC_WEB = "https://music.apple.com"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val BUNDLE_REGEX = Regex("""(/assets/[^"'>\s]+\.js)""")
        private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}""")
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        fun extractBundlePaths(html: String): List<String> {
            val all = BUNDLE_REGEX.findAll(html).map { it.groupValues[1] }.distinct().toList()
            val indexFirst = all.filter { it.contains("/assets/index~") || it.contains("/assets/index-") }
            return (indexFirst + all).distinct()
        }

        fun extractWebTokenFromJs(jsContent: String): WebTokenResult? {
            var fallback: WebTokenResult? = null
            for (match in JWT_REGEX.findAll(jsContent)) {
                val jwt = match.value
                val parsed = parseJwtMetadata(jwt) ?: continue
                if (parsed.issuer == "AMPWebPlay" || parsed.keyId == "WebPlayKid") {
                    return WebTokenResult(jwt, parsed.expiresAtEpochSec)
                }
                if (fallback == null && parsed.algorithm == "ES256") {
                    fallback = WebTokenResult(jwt, parsed.expiresAtEpochSec)
                }
            }
            return fallback
        }

        fun parseExpirationEpochSec(jwt: String): Long =
            parseJwtMetadata(jwt)?.expiresAtEpochSec ?: 0L

        private data class JwtMeta(
            val algorithm: String?,
            val keyId: String?,
            val issuer: String?,
            val expiresAtEpochSec: Long
        )

        private fun parseJwtMetadata(jwt: String): JwtMeta? {
            val parts = jwt.split('.')
            if (parts.size != 3) return null
            val headerObj = decodeJsonPart(parts[0]) ?: return null
            val payloadObj = decodeJsonPart(parts[1]) ?: return null
            val alg = (headerObj["alg"] as? JsonPrimitive)?.contentOrNull
            val kid = (headerObj["kid"] as? JsonPrimitive)?.contentOrNull
            val iss = (payloadObj["iss"] as? JsonPrimitive)?.contentOrNull
            val exp = (payloadObj["exp"] as? JsonPrimitive)?.longOrNull ?: 0L
            return JwtMeta(alg, kid, iss, exp)
        }

        private fun decodeJsonPart(segment: String): JsonObject? = runCatching {
            val bytes = Base64.getUrlDecoder().decode(segment)
            json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        }.getOrNull()
    }
}
