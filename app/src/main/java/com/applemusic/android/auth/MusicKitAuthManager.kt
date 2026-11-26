package com.applemusic.android.auth

import android.content.Context
import android.content.Intent
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.AuthenticationManager
import com.apple.android.sdk.authentication.TokenError
import com.apple.android.sdk.authentication.TokenProvider

sealed class AppleMusicSignInOutcome {
    data class Success(
        val musicUserToken: String,
        val storefrontId: String
    ) : AppleMusicSignInOutcome()

    data object Cancelled : AppleMusicSignInOutcome()
    data class Error(val message: String) : AppleMusicSignInOutcome()
}

/**
 * Integrates Apple's official MusicKitAuth SDK to sign users in with Apple Music
 * and obtain their Music User Token automatically.
 */
class MusicKitAuthManager(
    context: Context,
    private val tokenManager: TokenManager
) : TokenProvider {

    private val authManager: AuthenticationManager =
        AuthenticationFactory.createAuthenticationManager(context.applicationContext)

    override fun getDeveloperToken(): String =
        tokenManager.getCredentials().developerToken

    override fun getUserToken(): String? =
        tokenManager.getCredentials().musicUserToken.takeIf { it.isNotBlank() }

    override fun getAppleMusicStoreFrontId(): String? =
        tokenManager.getCredentials().appleMusicStorefrontId.takeIf { it.isNotBlank() }

    fun createSignInIntent(): Intent? {
        val devToken = getDeveloperToken()
        if (devToken.isBlank()) return null

        return authManager.createIntentBuilder(devToken)
            .setHideStartScreen(false)
            .setStartScreenMessage("Conecta tu cuenta de Apple Music para ver tu Home personalizado y reproducir música.")
            .build()
    }

    fun handleSignInResult(data: Intent?): AppleMusicSignInOutcome {
        val result = authManager.handleTokenResult(data)
        if (!result.isError) {
            val userToken = result.musicUserToken.orEmpty()
            val storefront = result.storefrontId?.takeIf { it.isNotBlank() }
                ?: tokenManager.getCredentials().storefront
            val appleMusicStorefront = result.appleMusicStorefrontId.orEmpty()

            if (userToken.isBlank()) {
                return AppleMusicSignInOutcome.Error("No se recibió Music User Token de Apple Music.")
            }

            tokenManager.saveRuntimeCredentials(
                musicUserToken = userToken,
                storefront = storefront,
                appleMusicStorefrontId = appleMusicStorefront
            )
            return AppleMusicSignInOutcome.Success(
                musicUserToken = userToken,
                storefrontId = storefront
            )
        }

        return when (result.error) {
            TokenError.USER_CANCELLED -> AppleMusicSignInOutcome.Cancelled
            TokenError.NO_SUBSCRIPTION -> AppleMusicSignInOutcome.Error(
                "Tu Apple ID no tiene una suscripción activa a Apple Music."
            )
            TokenError.SUBSCRIPTION_EXPIRED -> AppleMusicSignInOutcome.Error(
                "Tu suscripción a Apple Music ha expirado."
            )
            TokenError.TOKEN_FETCH_ERROR -> AppleMusicSignInOutcome.Error(
                "Apple rechazó el Developer Token. Verifica tu Team ID, Key ID y llave .p8."
            )
            else -> AppleMusicSignInOutcome.Error(
                "Error al iniciar sesión con Apple Music (${result.error})."
            )
        }
    }
}
