package com.mystic.store.ui

/**
 * Minimal Markdown -> HTML converter for rendering repo READMEs in a TextView.
 * Supports headings, code fences, lists, links, bold/italic/strikethrough-less
 * inline formatting and paragraphs. Anything it doesn't understand is shown
 * as escaped plain text.
 */
object Markdown {

    fun toHtml(markdown: String, baseUrl: String? = null): String {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val sb = StringBuilder()
        var inList: Char? = null
        var i = 0

        fun closeList() {
            if (inList != null) {
                sb.append(if (inList == 'u') "</ul>" else "</ol>")
                inList = null
            }
        }

        val headingRegex = Regex("^(#{1,6})\\s+(.*)$")

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Fenced code block
                trimmed.startsWith("```") -> {
                    closeList()
                    val buf = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        buf.append(escape(lines[i])).append("\n")
                        i++
                    }
                    sb.append("<pre>").append(buf).append("</pre>")
                    i++
                }
                // ATX headings
                headingRegex.matches(trimmed) -> {
                    closeList()
                    val m = headingRegex.matchEntire(trimmed)!!
                    val level = m.groupValues[1].length
                    sb.append("<h").append(level).append('>')
                        .append(inline(m.groupValues[2], baseUrl))
                        .append("</h").append(level).append('>')
                    i++
                }
                // Horizontal rule
                trimmed.matches(Regex("(-{3,}|\\*{3,}|_{3,})")) -> {
                    closeList()
                    sb.append("<hr>")
                    i++
                }
                // Markdown table
                trimmed.startsWith("|") -> {
                    closeList()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = tableCells(lines[i])
                        val isSeparator = cells.isNotEmpty() &&
                            cells.all { Regex(":?-{3,}:?").matches(it.trim()) }
                        if (!isSeparator) rows.add(cells)
                        i++
                    }
                    if (rows.isNotEmpty()) {
                        val header = rows.removeAt(0)
                        sb.append("<p>").append(
                            header.joinToString(" | ") { "<b>" + inline(it.trim(), baseUrl) + "</b>" }
                        ).append("</p>")
                        for (row in rows) {
                            sb.append("<p>")
                                .append(row.joinToString(" | ") { inline(it.trim(), baseUrl) })
                                .append("</p>")
                        }
                    }
                }
                // Unordered list item
                Regex("^[-*]\\s+(.*)$").matches(trimmed) -> {
                    if (inList != 'u') {
                        closeList()
                        sb.append("<ul>")
                        inList = 'u'
                    }
                    sb.append("<li>").append(inline(Regex("^[-*]\\s+(.*)$").matchEntire(trimmed)!!.groupValues[1], baseUrl)).append("</li>")
                    i++
                }
                // Ordered list item
                Regex("^\\d+[.)]\\s+(.*)$").matches(trimmed) -> {
                    if (inList != 'o') {
                        closeList()
                        sb.append("<ol>")
                        inList = 'o'
                    }
                    sb.append("<li>").append(inline(Regex("^\\d+[.)]\\s+(.*)$").matchEntire(trimmed)!!.groupValues[1], baseUrl)).append("</li>")
                    i++
                }
                // Blank line
                line.isBlank() -> {
                    closeList()
                    i++
                }
                // Paragraph
                else -> {
                    closeList()
                    val para = StringBuilder()
                    while (i < lines.size &&
                        lines[i].isNotBlank() &&
                        !lines[i].trim().startsWith("```") &&
                        !headingRegex.matches(lines[i].trim())
                    ) {
                        para.append(inline(lines[i], baseUrl)).append(' ')
                        i++
                    }
                    sb.append("<p>").append(para.toString().trim()).append("</p>")
                }
            }
        }
        closeList()
        return sb.toString()
    }

    private val imageRegex = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)")
    private val imageLinkRegex = Regex("\\[!\\[([^\\]]*)]\\(([^)\\s]+)\\)]\\(([^)\\s]+)\\)")
    private val linkRegex = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
    private val codeRegex = Regex("`([^`]+)`")
    private val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
    private val italicStarRegex = Regex("(?<!\\*)\\*([^*\\s][^*]*)\\*(?!\\*)")
    private val italicUnderscoreRegex = Regex("(?<!_)_([^_\\s][^_]*)_(?!_)")

    /** Simple inline HTML tags that are preserved instead of being escaped. */
    private val allowedTagRegex =
        Regex("</?\\s*(?:span|b|i|u|s|strong|em|br)(?:\\s+[^>]*)?\\s*/?>")

    private fun tableCells(line: String): List<String> {
        val s = line.trim().trimStart('|').trimEnd('|')
        return s.split('|').map { it.trim() }
    }

    /** Preserves whitelisted raw HTML (color spans etc.), formats the rest. */
    private fun inline(text: String, baseUrl: String? = null): String {
        val tokens = mutableListOf<String>()
        val protected = allowedTagRegex.replace(text) { m ->
            tokens.add(m.value)
            "\u0000${tokens.size - 1}\u0000"
        }
        var s = coreInline(protected, baseUrl)
        tokens.forEachIndexed { index, tag ->
            s = s.replace("\u0000$index\u0000", tag)
        }
        return s
    }

    private fun coreInline(text: String, baseUrl: String? = null): String {
        var s = escape(text)
        s = imageLinkRegex.replace(s) { m ->
            val alt = m.groupValues[1].replace("\"", "&quot;")
            var src = m.groupValues[2]
            if (!src.startsWith("http://") && !src.startsWith("https://") && baseUrl != null) {
                src = baseUrl.trimEnd('/') + "/" + src
            }
            "<a href=\"" + m.groupValues[3] + "\"><img src=\"$src\" alt=\"$alt\"/></a>"
        }
        s = imageRegex.replace(s) { m ->
            val alt = m.groupValues[1].replace("\"", "&quot;")
            var src = m.groupValues[2]
            if (!src.startsWith("http://") && !src.startsWith("https://") && baseUrl != null) {
                src = baseUrl.trimEnd('/') + "/" + src
            }
            "<a href=\"$src\"><img src=\"$src\" alt=\"$alt\"/></a>"
        }
        s = linkRegex.replace(s) { m ->
            "<a href=\"" + m.groupValues[2] + "\">" + m.groupValues[1] + "</a>"
        }
        s = codeRegex.replace(s) { m -> "<code>" + m.groupValues[1] + "</code>" }
        s = boldRegex.replace(s) { m -> "<b>" + m.groupValues[1] + "</b>" }
        s = italicStarRegex.replace(s) { m -> "<i>" + m.groupValues[1] + "</i>" }
        s = italicUnderscoreRegex.replace(s) { m -> "<i>" + m.groupValues[1] + "</i>" }
        return s
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}