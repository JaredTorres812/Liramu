package com.applemusic.android.data.api

import android.content.Context
import com.applemusic.android.BuildConfig
import com.applemusic.android.auth.TokenManager
import com.applemusic.android.auth.WebTokenProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class AppleMusicAuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = tokenManager.getCredentials()
        val requestBuilder = chain.request().newBuilder()
            .header("Accept", "application/json")

        if (creds.hasDeveloperToken) {
            requestBuilder.header("Authorization", "Bearer ${creds.developerToken}")
        }
        if (creds.hasUserToken) {
            requestBuilder.header("Music-User-Token", creds.musicUserToken)
        }

        return chain.proceed(requestBuilder.build())
    }
}

/**
 * Inyecta los headers de `music.apple.com` para peticiones HTTP directas
 * hacia `https://amp-api.music.apple.com/`.
 */
class AmpWebAuthInterceptor(
    private val tokenManager: TokenManager,
    private val webTokenProvider: WebTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = tokenManager.getCredentials()
        val webDevToken = tokenManager.getValidWebDeveloperToken()
            ?: runBlocking { webTokenProvider.getOrFetchWebToken() }
            ?: creds.webDeveloperToken

        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Origin", WebTokenProvider.APPLE_MUSIC_WEB)
            .header("Referer", "${WebTokenProvider.APPLE_MUSIC_WEB}/")
            .header("User-Agent", WebTokenProvider.WEB_USER_AGENT)

        if (webDevToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $webDevToken")
        }
        if (creds.webUserToken.isNotBlank()) {
            builder.header("Music-User-Token", creds.webUserToken)
            builder.header("media-user-token", creds.webUserToken)
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Si `amp-api` responde 401 porque rotó el Developer Token web, lo vuelve a extraer una vez
 * desde `music.apple.com` y reintenta la llamada automáticamente.
 */
class AmpWebAuthenticator(
    private val webTokenProvider: WebTokenProvider
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val refreshed = runBlocking { webTokenProvider.getOrFetchWebToken(forceRefresh = true) }
            ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshed")
            .build()
    }
}

object NetworkModule {

    private const val BASE_URL = "https://api.music.apple.com/"
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun createApiService(tokenManager: TokenManager, context: Context? = null): AppleMusicApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .apply {
                context?.let {
                    cache(Cache(File(it.cacheDir, "am_api_http_cache"), 32L * 1024L * 1024L))
                }
            }
            .addInterceptor(AppleMusicAuthInterceptor(tokenManager))
            .addNetworkInterceptor(ReadOnlyHttpCacheInterceptor())
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AppleMusicApiService::class.java)
    }

    fun createAmpApiService(
        tokenManager: TokenManager,
        webTokenProvider: WebTokenProvider,
        context: Context? = null
    ): AmpApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .apply {
                context?.let {
                    cache(Cache(File(it.cacheDir, "amp_http_cache"), 64L * 1024L * 1024L))
                }
            }
            .addInterceptor(AmpWebAuthInterceptor(tokenManager, webTokenProvider))
            .authenticator(AmpWebAuthenticator(webTokenProvider))
            .addNetworkInterceptor(ReadOnlyHttpCacheInterceptor())
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(AMP_BASE_URL)
            .client(okHttpClient)
            .build()
            .create(AmpApiService::class.java)
    }
}

/**
 * Habilita caché HTTP en disco para consultas GET de catálogo y contenido editorial
 * sin cachear jamás mutaciones, favoritos, ratings ni estado personal de `/v1/me/`.
 */
private class ReadOnlyHttpCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val response = chain.proceed(req)
        if (!req.method.equals("GET", ignoreCase = true)) return response
        val path = req.url.encodedPath
        val query = req.url.encodedQuery.orEmpty()
        val isCacheableCatalog =
            !path.contains("/v1/me/") &&
                !query.contains("personalRating") &&
                !query.contains("inLibrary") &&
                (path.contains("/v1/catalog/") || path.contains("/v1/editorial/") || path.contains("/v1/recommendations/"))
        if (!isCacheableCatalog) return response

        val cacheControl = CacheControl.Builder()
            .maxAge(3, TimeUnit.MINUTES)
            .build()
        return response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", cacheControl.toString())
            .build()
    }
}
