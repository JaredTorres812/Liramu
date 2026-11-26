package com.applemusic.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class WebTokenProviderTest {

    @Test
    fun extractsIndexBundleFirstIncludingTildeHash() {
        val html = """
            <script src="/assets/vendor~1234.js"></script>
            <script type="module" src="/assets/index~B8RKPfmaCI.js"></script>
        """.trimIndent()

        val paths = WebTokenProvider.extractBundlePaths(html)
        assertEquals(listOf("/assets/index~B8RKPfmaCI.js", "/assets/vendor~1234.js"), paths)
    }

    @Test
    fun picksAmpWebPlayTokenOverOtherJwtsInBundle() {
        val otherJwt = fakeJwt(
            header = """{"typ":"JWT","alg":"ES256","kid":"OTHER12345"}""",
            payload = """{"iss":"OTHERTEAM","exp":1799948708}"""
        )
        val ampJwt = fakeJwt(
            header = """{"typ":"JWT","alg":"ES256","kid":"WebPlayKid"}""",
            payload = """{"iss":"AMPWebPlay","iat":1793900708,"exp":1799948708,"root_https_origin":["apple.com"]}"""
        )
        val js = """const a="$otherJwt";const b="$ampJwt";"""

        val extracted = WebTokenProvider.extractWebTokenFromJs(js)
        assertNotNull(extracted)
        assertEquals(ampJwt, extracted?.token)
        assertEquals(1799948708L, extracted?.expiresAtEpochSec)
    }

    @Test
    fun returnsNullWhenNoJwtPresent() {
        assertNull(WebTokenProvider.extractWebTokenFromJs("console.log('no tokens');"))
    }

    private fun fakeJwt(header: String, payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val h = enc.encodeToString(header.toByteArray())
        val p = enc.encodeToString(payload.toByteArray())
        val sig = enc.encodeToString(ByteArray(64) { 1 })
        return "$h.$p.$sig"
    }
}
