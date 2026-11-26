package com.applemusic.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AppleMusicJwtGeneratorTest {

    @Test
    fun `generateDeveloperToken creates valid 3-part ES256 JWT`() {
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGen.generateKeyPair()

        val pkcs8Base64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$pkcs8Base64\n-----END PRIVATE KEY-----"

        val jwt = AppleMusicJwtGenerator.generateDeveloperToken(
            teamId = "TEAM123456",
            keyId = "KEY1234567",
            p8PrivateKeyPem = pem,
            nowEpochSeconds = 1_700_000_000L
        )

        val parts = jwt.split(".")
        assertEquals(3, parts.size)

        val headerJson = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val rawSig = Base64.getUrlDecoder().decode(parts[2])

        assertTrue(headerJson.contains("\"alg\":\"ES256\""))
        assertTrue(headerJson.contains("\"kid\":\"KEY1234567\""))
        assertTrue(payloadJson.contains("\"iss\":\"TEAM123456\""))
        assertEquals(64, rawSig.size)
    }
}
