package com.applemusic.android.auth

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Generates Apple Music Developer Tokens (ES256 JWT) from a MusicKit .p8 private key.
 */
object AppleMusicJwtGenerator {

    private const val MAX_LIFETIME_SECONDS = 15_552_000L // 180 days (Apple max is 6 months)

    fun generateDeveloperToken(
        teamId: String,
        keyId: String,
        p8PrivateKeyPem: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): String {
        require(teamId.isNotBlank()) { "Team ID cannot be blank" }
        require(keyId.isNotBlank()) { "Key ID cannot be blank" }
        require(p8PrivateKeyPem.isNotBlank()) { "Private key (.p8) cannot be blank" }

        val headerJson = """{"alg":"ES256","kid":"$keyId"}"""
        val exp = nowEpochSeconds + MAX_LIFETIME_SECONDS
        val payloadJson = """{"iss":"$teamId","iat":$nowEpochSeconds,"exp":$exp}"""

        val encodedHeader = base64UrlEncode(headerJson.toByteArray(Charsets.UTF_8))
        val encodedPayload = base64UrlEncode(payloadJson.toByteArray(Charsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"

        val privateKey = parseEcPrivateKey(p8PrivateKeyPem)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        val rawSignature = derToP1363(signature, 64)
        val encodedSignature = base64UrlEncode(rawSignature)
        return "$signingInput.$encodedSignature"
    }

    private fun parseEcPrivateKey(pem: String): ECPrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()

        val keyBytes = Base64.getDecoder().decode(cleaned)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(spec) as ECPrivateKey
    }

    /**
     * Converts an ASN.1 DER-encoded ECDSA signature into raw IEEE P1363 (r || s) format.
     */
    internal fun derToP1363(derSignature: ByteArray, outputLength: Int = 64): ByteArray {
        require(derSignature.size >= 8 && derSignature[0] == 0x30.toByte()) {
            "Invalid DER ECDSA signature"
        }

        val halfLen = outputLength / 2
        var offset = 2
        if ((derSignature[1].toInt() and 0x80) != 0) {
            offset += (derSignature[1].toInt() and 0x7f)
        }

        require(derSignature[offset] == 0x02.toByte()) { "Invalid DER integer tag for r" }
        val rLen = derSignature[offset + 1].toInt() and 0xff
        val rStart = offset + 2
        offset = rStart + rLen

        require(derSignature[offset] == 0x02.toByte()) { "Invalid DER integer tag for s" }
        val sLen = derSignature[offset + 1].toInt() and 0xff
        val sStart = offset + 2

        val raw = ByteArray(outputLength)
        copyUnsignedInt(derSignature, rStart, rLen, raw, 0, halfLen)
        copyUnsignedInt(derSignature, sStart, sLen, raw, halfLen, halfLen)
        return raw
    }

    private fun copyUnsignedInt(
        src: ByteArray,
        srcOffset: Int,
        srcLen: Int,
        dst: ByteArray,
        dstOffset: Int,
        dstLen: Int
    ) {
        var trimmedOffset = srcOffset
        var trimmedLen = srcLen
        while (trimmedLen > 0 && src[trimmedOffset] == 0.toByte()) {
            trimmedOffset++
            trimmedLen--
        }
        val copyLen = minOf(trimmedLen, dstLen)
        val srcCopyStart = trimmedOffset + (trimmedLen - copyLen)
        val dstCopyStart = dstOffset + (dstLen - copyLen)
        System.arraycopy(src, srcCopyStart, dst, dstCopyStart, copyLen)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
