package com.applemusic.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlLyricsParserTest {

    @Test
    fun lineSyncedTtmlDoesNotFabricateSingleWordSweep() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" itunes:timing="Line">
              <body>
                <div>
                  <p begin="00:10.000" end="00:15.000">Estoy cansado</p>
                  <p begin="00:15.500" end="00:19.000">De esperar</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = TtmlLyricsParser.parseTtml("123", ttml)!!
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("Estoy cansado", parsed.lines[0].text)
        assertEquals(10_000L, parsed.lines[0].startMs)
        assertEquals(15_000L, parsed.lines[0].endMs)
        assertTrue("Line-synced lyrics must not fabricate fake word timings", parsed.lines[0].words.isEmpty())
    }

    @Test
    fun syllableSyncedTtmlPreservesRealWordAndSyllableTimings() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" itunes:timing="Word">
              <body>
                <div>
                  <p begin="00:10.000" end="00:14.000">
                    <span begin="00:10.000" end="00:10.600">Es</span><span begin="00:10.600" end="00:12.000">toy</span>
                    <span begin="00:12.000" end="00:14.000">cansado</span>
                    <span ttm:role="x-bg" begin="00:12.500" end="00:14.000"><span begin="00:12.500" end="00:14.000">(muy cansado)</span></span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = TtmlLyricsParser.parseTtml("456", ttml)!!
        assertTrue(parsed.isSynced)
        assertEquals(1, parsed.lines.size)
        val line = parsed.lines[0]
        assertEquals("Estoy cansado", line.text)
        assertEquals(3, line.words.size)
        assertEquals("Es", line.words[0].text)
        assertEquals(10_000L, line.words[0].startMs)
        assertEquals(10_600L, line.words[0].endMs)
        assertEquals("toy", line.words[1].text)
        assertEquals(" cansado", line.words[2].text)
        assertEquals(12_000L, line.words[2].startMs)
        assertEquals(14_000L, line.words[2].endMs)
        assertEquals("(muy cansado)", line.backgroundText)
        assertEquals(1, line.backgroundWords.size)
    }
}
