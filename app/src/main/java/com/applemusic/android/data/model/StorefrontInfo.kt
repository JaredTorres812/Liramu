package com.applemusic.android.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/** Respuesta de /v1/me/storefront: país de la cuenta e idiomas que soporta. */
data class StorefrontInfo(
    val id: String,
    val defaultLanguage: String,
    val supportedLanguages: List<String>
) {
    /** Igual que Apple: el idioma del teléfono si la tienda lo soporta; si no, el de la tienda. */
    fun pickLanguage(device: Locale): String {
        val tag = device.toLanguageTag()
        supportedLanguages.firstOrNull { it.equals(tag, ignoreCase = true) }?.let { return it }
        if (defaultLanguage.substringBefore('-').equals(device.language, ignoreCase = true)) return defaultLanguage
        supportedLanguages.firstOrNull { it.substringBefore('-').equals(device.language, ignoreCase = true) }
            ?.let { return it }
        return defaultLanguage.ifBlank { tag }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): StorefrontInfo? {
            val sf = ((json.parseToJsonElement(body) as? JsonObject)?.get("data") as? JsonArray)
                ?.firstOrNull() as? JsonObject ?: return null
            val attrs = sf["attributes"] as? JsonObject
            val id = (sf["id"] as? JsonPrimitive)?.contentOrNull ?: return null
            return StorefrontInfo(
                id = id,
                defaultLanguage = (attrs?.get("defaultLanguageTag") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                supportedLanguages = (attrs?.get("supportedLanguageTags") as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            )
        }
    }
}
