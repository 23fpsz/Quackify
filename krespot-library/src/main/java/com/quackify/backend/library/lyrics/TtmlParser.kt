package com.quackify.backend.library.lyrics

object TtmlParser {

    private val SKIPPED_ROLES = setOf("x-translation", "x-roman")

    fun parse(ttml: String): List<SyncedLyricLine> = runCatching {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            harden("http://apache.org/xml/features/disallow-doctype-decl")
            harden("http://xml.org/sax/features/external-general-entities", false)
            harden("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching { isExpandEntityReferences = false }
        }
        val document = factory.newDocumentBuilder()
            .parse(org.xml.sax.InputSource(java.io.StringReader(ttml)))
        val paragraphs = document.getElementsByTagName("p")
        val lines = ArrayList<SyncedLyricLine>(paragraphs.length)
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as? org.w3c.dom.Element ?: continue
            lineFrom(paragraph)?.let(lines::add)
        }
        lines.sortBy { it.timeMs }
        lines
    }.getOrDefault(emptyList())

    private fun javax.xml.parsers.DocumentBuilderFactory.harden(feature: String, value: Boolean = true) {
        runCatching { setFeature(feature, value) }
    }

    private fun lineFrom(paragraph: org.w3c.dom.Element): SyncedLyricLine? {
        val pieces = mutableListOf<Piece>()
        collect(paragraph, pieces)
        val words = mergeIntoWords(pieces)
        if (words.isEmpty()) {
            val text = paragraph.textContent?.trim().orEmpty()
            val begin = time(paragraph.getAttribute("begin")) ?: return null
            if (text.isEmpty()) return null
            val end = time(paragraph.getAttribute("end"))?.takeIf { it > begin }
            return SyncedLyricLine(timeMs = begin, text = text, sungUntilMs = end)
        }
        val begin = time(paragraph.getAttribute("begin")) ?: words.first().startMs
        return SyncedLyricLine(
            timeMs = minOf(begin, words.first().startMs),
            text = words.joinToString(" ") { it.text },
            words = words,
        )
    }

    private fun collect(node: org.w3c.dom.Node, out: MutableList<Piece>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            when (val child = children.item(i)) {
                is org.w3c.dom.Element -> {
                    val role = child.qualified("ttm:role")
                    if (role in SKIPPED_ROLES) continue
                    val begin = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        out += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, out)
                    }
                }
                else -> if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                    val text = child.textContent.orEmpty()
                    if (text.isNotEmpty()) out += Piece.Text(text)
                }
            }
        }
    }

    private fun hasTimedChild(element: org.w3c.dom.Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    private fun mergeIntoWords(pieces: List<Piece>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        val syllables = mutableListOf<LyricSyllable>()
        var start = 0L
        var end = 0L
        var timed = false

        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) {
                words += LyricWord(start, end, text, LyricSyllable.normalized(syllables, text.length))
            }
            syllables.clear()
            timed = false
        }

        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    timed -> current.append(piece.text)
                    else -> Unit
                }
                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    val from = current.length
                    current.append(piece.text.trim())
                    syllables += LyricSyllable(piece.start, piece.end, from, current.length)
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val stripped = raw.removeSuffix("s")
        val parts = stripped.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m ->
                    parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private fun org.w3c.dom.Element.qualified(name: String): String {
        getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val found = attributes ?: return ""
        for (i in 0 until found.length) {
            val attribute = found.item(i) ?: continue
            if (attribute.nodeName == name ||
                attribute.nodeName == local ||
                attribute.localName == local
            ) {
                return attribute.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}
