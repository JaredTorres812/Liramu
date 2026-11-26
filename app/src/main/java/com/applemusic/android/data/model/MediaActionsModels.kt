package com.applemusic.android.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

data class QueueTrackItem(
    val index: Int,
    val id: String,
    val catalogId: String?,
    val type: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val durationMs: Long
)

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val isInstrumental: Boolean = false,
    val agent: String? = null,
    val isSecondaryAgent: Boolean = false,
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList(),
    val translation: String? = null
)

data class SongLyrics(
    val songId: String,
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainText: String,
    val songwriterCredit: String? = null
)

data class CreditPerson(
    val name: String,
    val roleNames: List<String>,
    val artistId: String?,
    val artworkUrl: String?
)

data class CreditGroup(
    val title: String,
    val people: List<CreditPerson>
)

data class SongCreditsDetail(
    val songId: String,
    val title: String,
    val artistName: String,
    val albumName: String?,
    val albumId: String?,
    val primaryArtistId: String?,
    val artworkUrl: String?,
    val releaseDate: String?,
    val audioTraits: List<String>,
    val groups: List<CreditGroup>,
    val lyricsExcerpt: String?
)

data class ItemAccountStatus(
    val id: String,
    val catalogId: String? = null,
    val libraryId: String? = null,
    val isFavorite: Boolean = false,
    val inLibrary: Boolean = false,
    val albumId: String? = null,
    val artistId: String? = null
)

object TtmlLyricsParser {

    private val P_TAG_PATTERN = Pattern.compile(
        "<p\\b([^>]*)>(.*?)</p>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )
    private val SPAN_TAG_PATTERN = Pattern.compile(
        "<span\\b([^>]*)>(.*?)</span>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )
    private val ATTR_BEGIN = Pattern.compile("\\bbegin\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_END = Pattern.compile("\\bend\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    private val TAG_STRIP = Pattern.compile("<[^>]+>")
    private val SONGWRITER_PATTERN = Pattern.compile(
        "<songwriter\\b[^>]*>(.*?)</songwriter>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    /** Parsea TTML oficial de Apple Music (`/syllable-lyrics` o `/lyrics`), conservando tiempos de línea y palabra. */
    fun parseTtml(songId: String, ttml: String): SongLyrics? {
        if (ttml.isBlank()) return null

        parseTtmlDom(songId, ttml)?.let { return it }

        val songwriters = mutableListOf<String>()
        val swMatcher = SONGWRITER_PATTERN.matcher(ttml)
        while (swMatcher.find()) {
            val cleanSw = decodeEntities(TAG_STRIP.matcher(swMatcher.group(1).orEmpty()).replaceAll("")).trim()
            if (cleanSw.isNotEmpty()) songwriters += cleanSw
        }

        val timingNone = ttml.contains("itunes:timing=\"None\"", ignoreCase = true) ||
            ttml.contains("itunes:timing='None'", ignoreCase = true)

        val lines = mutableListOf<LyricLine>()
        var hasAnyTiming = false
        val pMatcher = P_TAG_PATTERN.matcher(ttml)
        while (pMatcher.find()) {
            val attrs = pMatcher.group(1).orEmpty()
            val innerHtml = pMatcher.group(2).orEmpty()

            val lineStart = extractAttr(ATTR_BEGIN, attrs)?.let { parseTimeMs(it) }
            val lineEnd = extractAttr(ATTR_END, attrs)?.let { parseTimeMs(it) }
            if (lineStart != null && !timingNone) hasAnyTiming = true

            val words = mutableListOf<LyricWord>()
            val sMatcher = SPAN_TAG_PATTERN.matcher(innerHtml)
            while (sMatcher.find()) {
                val sAttrs = sMatcher.group(1).orEmpty()
                val rawWord = decodeEntities(
                    TAG_STRIP.matcher(sMatcher.group(2).orEmpty()).replaceAll("")
                )
                if (rawWord.isBlank()) continue
                val parsedSpanBegin = extractAttr(ATTR_BEGIN, sAttrs)?.let { parseTimeMs(it) }
                val parsedSpanEnd = extractAttr(ATTR_END, sAttrs)?.let { parseTimeMs(it) }
                val wStart = parsedSpanBegin ?: lineStart ?: 0L
                val wEnd = parsedSpanEnd ?: lineEnd ?: (wStart + 400L)
                if (parsedSpanBegin != null && !timingNone) hasAnyTiming = true
                words += LyricWord(startMs = wStart, endMs = wEnd, text = rawWord)
            }

            val rawLineText = innerHtml.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            val cleanText = decodeEntities(TAG_STRIP.matcher(rawLineText).replaceAll(""))
                .replace(Regex("[ \\t]+"), " ")
                .trim()

            if (cleanText.isNotEmpty()) {
                val startMs = lineStart ?: words.firstOrNull()?.startMs ?: -1L
                val endMs = lineEnd ?: words.lastOrNull()?.endMs ?: (startMs + 4000L)
                lines += LyricLine(
                    startMs = startMs.coerceAtLeast(0L),
                    endMs = endMs.coerceAtLeast(startMs.coerceAtLeast(0L)),
                    text = cleanText,
                    words = words,
                    isInstrumental = false
                )
            }
        }

        if (lines.isEmpty()) {
            val fallbackText = decodeEntities(TAG_STRIP.matcher(ttml).replaceAll("\n"))
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (fallbackText.isEmpty()) return null
            val fallbackLines = fallbackText.map {
                LyricLine(startMs = 0L, endMs = 0L, text = it)
            }
            return SongLyrics(
                songId = songId,
                isSynced = false,
                lines = fallbackLines,
                plainText = fallbackText.joinToString("\n"),
                songwriterCredit = songwriters.takeIf { it.isNotEmpty() }?.joinToString(", ")
            )
        }

        val sorted = if (hasAnyTiming) lines.sortedBy { it.startMs } else lines
        return SongLyrics(
            songId = songId,
            isSynced = hasAnyTiming,
            lines = sorted,
            plainText = sorted.joinToString("\n") { it.text },
            songwriterCredit = songwriters.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
    }

    private fun parseTtmlDom(songId: String, ttml: String): SongLyrics? {
        val doc = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        }.getOrNull() ?: return null

        val root = doc.documentElement ?: return null
        val tickRate = root.attrAny("ttp:tickRate", "tickRate")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1000.0
        val frameRate = root.attrAny("ttp:frameRate", "frameRate")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 30.0
        val timingMode = root.attrAny("itunes:timing", "timing")?.lowercase().orEmpty()
        val timingDisabled = timingMode == "none"

        val songwriters = mutableListOf<String>()
        val swNodes = doc.getElementsByTagName("*")
        for (i in 0 until swNodes.length) {
            val el = swNodes.item(i) as? Element ?: continue
            val local = el.tagName.substringAfter(':').lowercase()
            if (local == "songwriter") {
                val txt = el.textContent?.trim().orEmpty()
                if (txt.isNotEmpty() && txt !in songwriters) songwriters += txt
            }
        }

        val pNodes = doc.getElementsByTagName("*")
        val lines = mutableListOf<LyricLine>()
        var hasAnyTiming = false
        var primaryAgent: String? = null

        for (i in 0 until pNodes.length) {
            val el = pNodes.item(i) as? Element ?: continue
            if (!el.tagName.substringAfter(':').equals("p", ignoreCase = true)) continue

            // Soporte por si el contenedor <div> tiene `begin` con hijos relativos cuando timeContainer="seq"
            var parentOffsetMs = 0L
            var parent = el.parentNode
            while (parent is Element) {
                val tc = parent.attrAny("timeContainer")?.lowercase()
                val pBegin = parent.attrAny("begin")?.let { parseTimeMs(it, tickRate, frameRate) }
                if (tc == "seq" && pBegin != null) {
                    parentOffsetMs += pBegin
                }
                parent = parent.parentNode
            }

            val rawPStart = el.attrAny("begin")?.let { parseTimeMs(it, tickRate, frameRate) }?.plus(parentOffsetMs)
            val rawPEnd = el.attrAny("end")?.let { parseTimeMs(it, tickRate, frameRate) }?.plus(parentOffsetMs)
            if (rawPStart != null && !timingDisabled) hasAnyTiming = true

            val lineAgent = el.attrAny("ttm:agent", "agent")?.trim()?.takeIf { it.isNotEmpty() }
            if (lineAgent != null && primaryAgent == null) {
                primaryAgent = lineAgent
            }
            val isSecondary = lineAgent != null && primaryAgent != null && lineAgent != primaryAgent

            val words = mutableListOf<LyricWord>()
            val textBuilder = StringBuilder()
            val bgWords = mutableListOf<LyricWord>()
            val bgTextBuilder = StringBuilder()
            val translationBuilder = StringBuilder()
            var pendingMainSpace = false
            var pendingBgSpace = false

            fun walk(node: Node, inheritedStart: Long?, inheritedEnd: Long?, inBackground: Boolean) {
                when (node.nodeType) {
                    Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                        val rawTxt = node.nodeValue.orEmpty()
                        if (rawTxt.isEmpty()) return
                        val collapsed = rawTxt.replace(Regex("[\\r\\n\\t]+"), " ")
                        val isOnlySpace = collapsed.isBlank()
                        if (inBackground) {
                            bgTextBuilder.append(collapsed)
                            if (isOnlySpace) {
                                if (bgWords.isNotEmpty()) pendingBgSpace = true
                            } else if (inheritedStart != null) {
                                val wEnd = (inheritedEnd ?: (inheritedStart + 400L)).coerceAtLeast(inheritedStart + 40L)
                                val prefix = if (pendingBgSpace && !collapsed.startsWith(" ")) " " else ""
                                pendingBgSpace = false
                                bgWords += LyricWord(startMs = inheritedStart, endMs = wEnd, text = prefix + collapsed)
                            }
                        } else {
                            textBuilder.append(collapsed)
                            if (isOnlySpace) {
                                if (words.isNotEmpty()) pendingMainSpace = true
                            } else if (inheritedStart != null) {
                                val wEnd = (inheritedEnd ?: (inheritedStart + 400L)).coerceAtLeast(inheritedStart + 40L)
                                val prefix = if (pendingMainSpace && !collapsed.startsWith(" ")) " " else ""
                                pendingMainSpace = false
                                words += LyricWord(startMs = inheritedStart, endMs = wEnd, text = prefix + collapsed)
                            }
                        }
                    }
                    Node.ELEMENT_NODE -> {
                        val childEl = node as Element
                        val tag = childEl.tagName.substringAfter(':').lowercase()
                        if (tag == "br") {
                            if (inBackground) bgTextBuilder.append('\n') else textBuilder.append('\n')
                            return
                        }
                        val role = childEl.attrAny("ttm:role", "role")?.lowercase().orEmpty()
                        // Capturar traducciones nativas de Apple Music (x-translation) e ignorar romanizaciones (x-roman)
                        if (role == "x-translation") {
                            val tr = childEl.textContent?.replace(Regex("[\\r\\n\\t]+"), " ")?.trim().orEmpty()
                            if (tr.isNotEmpty()) {
                                if (translationBuilder.isNotEmpty()) translationBuilder.append(' ')
                                translationBuilder.append(tr)
                            }
                            return
                        }
                        if (role == "x-roman") return
                        val childInBg = inBackground || role == "x-bg"
                        val cStart = childEl.attrAny("begin")?.let { parseTimeMs(it, tickRate, frameRate) }?.plus(parentOffsetMs)
                        val cEnd = childEl.attrAny("end")?.let { parseTimeMs(it, tickRate, frameRate) }?.plus(parentOffsetMs)
                        if (cStart != null && !timingDisabled) hasAnyTiming = true
                        val nextStart = cStart ?: inheritedStart
                        val nextEnd = cEnd ?: inheritedEnd
                        val children = childEl.childNodes
                        for (c in 0 until children.length) {
                            walk(children.item(c), nextStart, nextEnd, childInBg)
                        }
                    }
                }
            }

            val pChildren = el.childNodes
            for (c in 0 until pChildren.length) {
                walk(pChildren.item(c), rawPStart, rawPEnd, inBackground = false)
            }

            val cleanText = textBuilder.toString()
                .replace(Regex("[ \\t\\r]+"), " ")
                .lines()
                .joinToString("\n") { it.trim() }
                .trim()
            val cleanBgText = bgTextBuilder.toString()
                .replace(Regex("[ \\t\\r]+"), " ")
                .lines()
                .joinToString("\n") { it.trim() }
                .trim()
                .takeIf { it.isNotEmpty() }
            val cleanTranslation = translationBuilder.toString()
                .replace(Regex("[ \\t\\r]+"), " ")
                .trim()
                .takeIf { it.isNotEmpty() }

            if (cleanText.isEmpty() && cleanBgText == null) continue

            val effectiveMainText = cleanText.ifEmpty { cleanBgText.orEmpty() }
            val effectiveWords = if (cleanText.isNotEmpty()) words else bgWords
            val effectiveBgText = if (cleanText.isNotEmpty()) cleanBgText else null
            val effectiveBgWords = if (cleanText.isNotEmpty()) bgWords else emptyList()

            val startMs = (rawPStart ?: effectiveWords.firstOrNull()?.startMs ?: effectiveBgWords.firstOrNull()?.startMs ?: 0L).coerceAtLeast(0L)
            val endMs = (rawPEnd ?: effectiveWords.lastOrNull()?.endMs ?: effectiveBgWords.lastOrNull()?.endMs ?: (startMs + 4000L)).coerceAtLeast(startMs)
            lines += LyricLine(
                startMs = startMs,
                endMs = endMs,
                text = effectiveMainText,
                words = effectiveWords,
                isInstrumental = false,
                agent = lineAgent,
                isSecondaryAgent = isSecondary,
                backgroundText = effectiveBgText,
                backgroundWords = effectiveBgWords,
                translation = cleanTranslation
            )
        }

        if (lines.isEmpty()) return null
        val sorted = if (hasAnyTiming) lines.sortedBy { it.startMs } else lines
        return SongLyrics(
            songId = songId,
            isSynced = hasAnyTiming,
            lines = sorted,
            plainText = sorted.joinToString("\n") { it.text },
            songwriterCredit = songwriters.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
    }

    private fun Element.attrAny(vararg names: String): String? {
        for (name in names) {
            val v = getAttribute(name)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /** Extrae `attributes.ttml` o `attributes.playParams` de una respuesta JSON de `/syllable-lyrics`, `/lyrics` o `?include=lyrics`. */
    fun parseLyricsResponse(songId: String, body: String): SongLyrics? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val ttmlFromData = root["data"]?.let { it as? JsonArray }
            ?.firstOrNull()?.let { it as? JsonObject }
            ?.get("attributes")?.let { it as? JsonObject }
            ?.get("ttml")?.jsonPrimitive?.contentOrNull
        if (!ttmlFromData.isNullOrBlank()) return parseTtml(songId, ttmlFromData)

        // Fallback cuando viene dentro de `resources.lyrics` o `resources.syllable-lyrics`
        val resources = root["resources"] as? JsonObject
        val ttmlFromRes = listOf("syllable-lyrics", "lyrics").firstNotNullOfOrNull { key ->
            val map = resources?.get(key) as? JsonObject ?: return@firstNotNullOfOrNull null
            map.values.firstNotNullOfOrNull { el ->
                (el as? JsonObject)?.get("attributes")?.let { it as? JsonObject }
                    ?.get("ttml")?.jsonPrimitive?.contentOrNull
            }
        }
        if (!ttmlFromRes.isNullOrBlank()) return parseTtml(songId, ttmlFromRes)

        return null
    }

    private fun extractAttr(pattern: Pattern, attrs: String): String? {
        val m = pattern.matcher(attrs)
        return if (m.find()) m.group(1) else null
    }

    internal fun parseTimeMs(raw: String, tickRate: Double = 1000.0, frameRate: Double = 30.0): Long? {
        val t = raw.trim().replace(',', '.')
        if (t.isEmpty()) return null
        if (t.endsWith("ms", ignoreCase = true)) {
            return t.dropLast(2).toDoubleOrNull()?.toLong()
        }
        if (t.endsWith("s", ignoreCase = true)) {
            return (t.dropLast(1).toDoubleOrNull()?.times(1000.0))?.toLong()
        }
        if (t.endsWith("m", ignoreCase = true)) {
            return (t.dropLast(1).toDoubleOrNull()?.times(60_000.0))?.toLong()
        }
        if (t.endsWith("h", ignoreCase = true)) {
            return (t.dropLast(1).toDoubleOrNull()?.times(3_600_000.0))?.toLong()
        }
        if (t.endsWith("t", ignoreCase = true)) {
            val ticks = t.dropLast(1).toDoubleOrNull() ?: return null
            return ((ticks / tickRate) * 1000.0).toLong()
        }
        val parts = t.split(":")
        return when (parts.size) {
            1 -> parts[0].toDoubleOrNull()?.times(1000.0)?.toLong()
            2 -> {
                val mins = parts[0].toDoubleOrNull() ?: return null
                val secs = parts[1].toDoubleOrNull() ?: return null
                ((mins * 60.0 + secs) * 1000.0).toLong()
            }
            3 -> {
                val hrs = parts[0].toDoubleOrNull() ?: return null
                val mins = parts[1].toDoubleOrNull() ?: return null
                val secs = parts[2].toDoubleOrNull() ?: return null
                ((hrs * 3600.0 + mins * 60.0 + secs) * 1000.0).toLong()
            }
            4 -> {
                val hrs = parts[0].toDoubleOrNull() ?: return null
                val mins = parts[1].toDoubleOrNull() ?: return null
                val secs = parts[2].toDoubleOrNull() ?: return null
                val frames = parts[3].toDoubleOrNull() ?: return null
                ((hrs * 3600.0 + mins * 60.0 + secs + frames / frameRate) * 1000.0).toLong()
            }
            else -> null
        }
    }

    private fun decodeEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

object MediaDetailsParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSongCredits(body: String): SongCreditsDetail? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val songObj = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val resources = root["resources"] as? JsonObject
        val attrs = songObj["attributes"] as? JsonObject ?: return null
        val songId = songObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val rels = songObj["relationships"] as? JsonObject

        val albumRef = (rels?.get("albums") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
        val albumId = albumRef?.get("id")?.jsonPrimitive?.contentOrNull
        val artistRef = (rels?.get("artists") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
        val primaryArtistId = artistRef?.get("id")?.jsonPrimitive?.contentOrNull

        val creditRefs = (rels?.get("credits") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }.orEmpty()
        val creditMap = resources?.get("credits") as? JsonObject
        val creditArtistsMap = resources?.get("credit-artists") as? JsonObject
        val artistsMap = resources?.get("artists") as? JsonObject

        val groups = creditRefs.mapNotNull { refEl ->
            val ref = refEl as? JsonObject ?: return@mapNotNull null
            val cid = ref["id"]?.jsonPrimitive?.contentOrNull
            val cObj = (if (ref["attributes"] != null) ref else cid?.let { creditMap?.get(it) as? JsonObject })
                ?: return@mapNotNull null
            val cAttrs = cObj["attributes"] as? JsonObject ?: return@mapNotNull null
            val groupTitle = cAttrs["title"]?.jsonPrimitive?.contentOrNull
                ?: cAttrs["kind"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val caRefs = ((cObj["relationships"] as? JsonObject)
                ?.get("credit-artists") as? JsonObject)
                ?.get("data")?.let { it as? JsonArray }.orEmpty()

            val people = caRefs.mapNotNull { caEl ->
                val caRef = caEl as? JsonObject ?: return@mapNotNull null
                val caId = caRef["id"]?.jsonPrimitive?.contentOrNull
                val caObj = (if (caRef["attributes"] != null) caRef else caId?.let { creditArtistsMap?.get(it) as? JsonObject })
                    ?: return@mapNotNull null
                val caAttrs = caObj["attributes"] as? JsonObject ?: return@mapNotNull null
                val name = caAttrs["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val roles = (caAttrs["roleNames"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                val linkedArtistId = ((caObj["relationships"] as? JsonObject)
                    ?.get("artists") as? JsonObject)
                    ?.get("data")?.let { it as? JsonArray }
                    ?.firstOrNull()?.let { it as? JsonObject }
                    ?.get("id")?.jsonPrimitive?.contentOrNull
                val linkedArtistAttrs = linkedArtistId?.let { artistsMap?.get(it) as? JsonObject }
                    ?.get("attributes") as? JsonObject
                val artTemplate = (caAttrs["artwork"] as? JsonObject ?: linkedArtistAttrs?.get("artwork") as? JsonObject)
                    ?.get("url")?.jsonPrimitive?.contentOrNull
                CreditPerson(
                    name = name,
                    roleNames = roles,
                    artistId = linkedArtistId,
                    artworkUrl = artTemplate
                        ?.replace("{w}", "200")
                        ?.replace("{h}", "200")
                        ?.replace("{c}", "cc")
                        ?.replace("{f}", "jpg")
                )
            }
            if (people.isEmpty()) null else CreditGroup(title = groupTitle, people = people)
        }

        val traits = (attrs["audioTraits"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.map { trait ->
                when (trait.lowercase()) {
                    "atmos" -> "Dolby Atmos"
                    "lossless" -> "Lossless"
                    "hi-res-lossless" -> "Lossless alta resolución"
                    "spatial" -> "Audio espacial"
                    else -> trait
                }
            }
            .orEmpty()

        val artTemplate = (attrs["artwork"] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
        return SongCreditsDetail(
            songId = songId,
            title = attrs["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artistName = attrs["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            albumName = attrs["albumName"]?.jsonPrimitive?.contentOrNull,
            albumId = albumId,
            primaryArtistId = primaryArtistId,
            artworkUrl = artTemplate
                ?.replace("{w}", "400")
                ?.replace("{h}", "400")
                ?.replace("{c}", "bb")
                ?.replace("{f}", "jpg"),
            releaseDate = attrs["releaseDate"]?.jsonPrimitive?.contentOrNull,
            audioTraits = traits,
            groups = groups,
            lyricsExcerpt = attrs["lyricsExcerpt"]?.jsonPrimitive?.contentOrNull
        )
    }

    fun parseItemStatus(itemId: String, body: String): ItemAccountStatus? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val obj = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val attrs = obj["attributes"] as? JsonObject
        val rels = obj["relationships"] as? JsonObject
        val resources = root["resources"] as? JsonObject

        val libRef = (rels?.get("library") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
        val catRef = (rels?.get("catalog") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
        val albumRef = (rels?.get("albums") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
        val artistRef = (rels?.get("artists") as? JsonObject)
            ?.get("data")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject

        val catalogId = catRef?.get("id")?.jsonPrimitive?.contentOrNull
            ?: (attrs?.get("playParams") as? JsonObject)?.get("catalogId")?.jsonPrimitive?.contentOrNull
            ?: (attrs?.get("playParams") as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?: obj["id"]?.jsonPrimitive?.contentOrNull?.takeUnless { it.startsWith("i.") || it.startsWith("l.") || it.startsWith("p.") }
        val rating = attrs?.get("personalRating")?.jsonPrimitive?.intOrNull
        val inFavAttr = attrs?.get("inFavorites")?.jsonPrimitive?.booleanOrNull
        val inLibAttr = attrs?.get("inLibrary")?.jsonPrimitive?.booleanOrNull

        // Si el endpoint de librería incluyó el recurso `catalog`, aprovechar su `personalRating` / `inFavorites` / `albums` / `artists`
        val catType = catRef?.get("type")?.jsonPrimitive?.contentOrNull
        val catObj = if (catalogId != null && catType != null) {
            (resources?.get(catType) as? JsonObject)?.get(catalogId) as? JsonObject
        } else null
        val catAttrs = catObj?.get("attributes") as? JsonObject
        val catRels = catObj?.get("relationships") as? JsonObject
        val catRating = catAttrs?.get("personalRating")?.jsonPrimitive?.intOrNull
        val catInFav = catAttrs?.get("inFavorites")?.jsonPrimitive?.booleanOrNull

        val resolvedAlbumId = albumRef?.get("id")?.jsonPrimitive?.contentOrNull
            ?: (catRels?.get("albums") as? JsonObject)
                ?.get("data")?.let { it as? JsonArray }?.firstOrNull()?.let { it as? JsonObject }
                ?.get("id")?.jsonPrimitive?.contentOrNull
        val resolvedArtistId = artistRef?.get("id")?.jsonPrimitive?.contentOrNull
            ?: (catRels?.get("artists") as? JsonObject)
                ?.get("data")?.let { it as? JsonArray }?.firstOrNull()?.let { it as? JsonObject }
                ?.get("id")?.jsonPrimitive?.contentOrNull

        return ItemAccountStatus(
            id = itemId,
            catalogId = catalogId,
            libraryId = libRef?.get("id")?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("i.") || it.startsWith("l.") || it.startsWith("p.") },
            isFavorite = (rating == 1) || (catRating == 1) || (inFavAttr == true) || (catInFav == true),
            inLibrary = inLibAttr == true || libRef != null || itemId.startsWith("i.") || itemId.startsWith("l.") || itemId.startsWith("p."),
            albumId = resolvedAlbumId,
            artistId = resolvedArtistId
        )
    }

    /** Extrae el conjunto de IDs con `attributes.value == 1` desde `/v1/me/ratings/{type}?ids=...`. */
    fun parseFavoriteIdsFromRatings(body: String): Set<String> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptySet()
        val data = root["data"] as? JsonArray ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (el in data) {
            val obj = el as? JsonObject ?: continue
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val value = (obj["attributes"] as? JsonObject)?.get("value")?.jsonPrimitive?.intOrNull
            if (value == 1) out += id
        }
        return out
    }
}
