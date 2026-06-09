package com.dlam.textediting.editor

import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

/**
 * Simple regex-based syntax highlighting for common programming languages.
 *
 * All work is done on a background thread; results are returned as a list of
 * [SpanCommand]s that the caller applies on the main thread via [applyTo].
 *
 * Language auto-detection is based on file extension (see [detectLanguage]).
 */
object SyntaxHighlighter {

    // ── colour palette (ARGB ints) ──

    object Colors {
        // Light theme
        val LIGHT_KEYWORD = 0xFF0033B3.toInt()
        val LIGHT_STRING = 0xFF067D17.toInt()
        val LIGHT_COMMENT = 0xFF8C8C8C.toInt()
        val LIGHT_NUMBER = 0xFF1750EB.toInt()
        val LIGHT_ANNOTATION = 0xFFB07C00.toInt()

        // Dark theme
        val DARK_KEYWORD = 0xFFCC7832.toInt()
        val DARK_STRING = 0xFF6AAB73.toInt()
        val DARK_COMMENT = 0xFF808080.toInt()
        val DARK_NUMBER = 0xFF6897BB.toInt()
        val DARK_ANNOTATION = 0xFFBBB529.toInt()
    }

    enum class SpanType { KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION }

    data class SpanCommand(
        val start: Int,
        val end: Int,
        val type: SpanType
    )

    /** Apply a list of [SpanCommand]s to a [Spannable]. */
    fun applyTo(spannable: Spannable, commands: List<SpanCommand>, darkMode: Boolean) {
        val keywordColor = if (darkMode) Colors.DARK_KEYWORD else Colors.LIGHT_KEYWORD
        val stringColor = if (darkMode) Colors.DARK_STRING else Colors.LIGHT_STRING
        val commentColor = if (darkMode) Colors.DARK_COMMENT else Colors.LIGHT_COMMENT
        val numberColor = if (darkMode) Colors.DARK_NUMBER else Colors.LIGHT_NUMBER
        val annotationColor = if (darkMode) Colors.DARK_ANNOTATION else Colors.LIGHT_ANNOTATION

        for (cmd in commands) {
            if (cmd.start < 0 || cmd.end > spannable.length || cmd.start >= cmd.end) continue
            val color = when (cmd.type) {
                SpanType.KEYWORD -> keywordColor
                SpanType.STRING -> stringColor
                SpanType.COMMENT -> commentColor
                SpanType.NUMBER -> numberColor
                SpanType.ANNOTATION -> annotationColor
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                cmd.start, cmd.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** Clear all syntax-highlighting spans from a [Spannable]. */
    fun clearSpans(spannable: Spannable) {
        val spans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        for (span in spans) {
            spannable.removeSpan(span)
        }
    }

    /**
     * Detect the programming language from a file extension.
     * Returns a [LanguageRules] or null if the extension isn't supported.
     */
    fun detectLanguage(fileName: String): LanguageRules? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> kotlinRules
            "java" -> javaRules
            "py" -> pythonRules
            "js", "ts", "jsx", "tsx" -> javascriptRules
            "c", "cpp", "h", "hpp", "go", "rs" -> cStyleRules
            "xml", "html", "htm" -> xmlRules
            "json" -> jsonRules
            "css" -> cssRules
            "sh", "bat" -> shellRules
            "rb" -> rubyRules
            "php" -> phpRules
            "sql" -> sqlRules
            "md" -> markdownRules
            else -> null // not a supported language
        }
    }

    // ── language definitions ──

    data class LanguageRules(
        val name: String,
        val keywords: Set<String>,
        val lineComment: String?,
        val blockCommentStart: String?,
        val blockCommentEnd: String?,
        val stringDelimiters: List<String>,
        val numberPattern: Pattern?,
        val keywordPattern: Pattern? = null
    )

    // ── Kotlin ──
    private val kotlinRules = LanguageRules(
        name = "Kotlin",
        keywords = setOf(
            "fun", "val", "var", "class", "object", "interface", "enum", "data",
            "sealed", "if", "else", "when", "for", "while", "do", "return", "break",
            "continue", "try", "catch", "finally", "throw", "import", "package",
            "override", "open", "abstract", "final", "private", "protected", "public",
            "internal", "constructor", "init", "this", "super", "true", "false", "null",
            "is", "as", "in", "out", "where", "typealias", "inline", "suspend",
            "companion", "const", "lateinit", "by", "lazy", "let", "also", "apply",
            "run", "with", "also", "takeIf", "takeUnless", "repeat", "require",
            "check", "assert", "TODO", "println", "print", "readLine"
        ),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("\"", "\"\"\""),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[fFLl]?\\b")
    )

    // ── Java ──
    private val javaRules = LanguageRules(
        name = "Java",
        keywords = setOf(
            "class", "interface", "enum", "extends", "implements", "public",
            "private", "protected", "static", "final", "abstract", "synchronized",
            "volatile", "transient", "native", "strictfp", "void", "int", "long",
            "double", "float", "boolean", "char", "byte", "short", "if", "else",
            "switch", "case", "default", "for", "while", "do", "break", "continue",
            "return", "throw", "throws", "try", "catch", "finally", "import",
            "package", "new", "this", "super", "true", "false", "null", "instanceof"
        ),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("\""),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[fFdDlL]?\\b")
    )

    // ── Python ──
    private val pythonRules = LanguageRules(
        name = "Python",
        keywords = setOf(
            "def", "class", "if", "elif", "else", "for", "while", "break",
            "continue", "return", "yield", "import", "from", "as", "try",
            "except", "finally", "raise", "with", "pass", "lambda", "global",
            "nonlocal", "assert", "del", "in", "is", "not", "and", "or",
            "True", "False", "None", "self", "print"
        ),
        lineComment = "#",
        blockCommentStart = "\"\"\"",
        blockCommentEnd = "\"\"\"",
        stringDelimiters = listOf("\"", "'", "\"\"\"", "'''"),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[jJ]?\\b")
    )

    // ── JavaScript / TypeScript ──
    private val javascriptRules = LanguageRules(
        name = "JavaScript",
        keywords = setOf(
            "function", "const", "let", "var", "class", "extends", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "return",
            "throw", "try", "catch", "finally", "import", "export", "default",
            "from", "new", "this", "super", "true", "false", "null", "undefined",
            "typeof", "instanceof", "async", "await", "yield", "of", "in",
            "console", "log", "debug"
        ),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("\"", "'", "`"),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[eE][+-]?\\d+\\b|\\b\\d+(\\.\\d+)?\\b")
    )

    // ── C / C++ / Go / Rust (C-style) ──
    private val cStyleRules = LanguageRules(
        name = "C-Style",
        keywords = setOf(
            "if", "else", "for", "while", "do", "switch", "case", "break",
            "continue", "return", "goto", "struct", "enum", "union", "typedef",
            "sizeof", "const", "static", "extern", "volatile", "register",
            "auto", "void", "int", "long", "float", "double", "char", "short",
            "unsigned", "signed", "true", "false", "NULL", "nullptr", "fn",
            "let", "mut", "pub", "impl", "trait", "match", "use", "mod",
            "package", "func", "defer", "go", "chan", "map", "type", "var"
        ),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("\""),
        numberPattern = Pattern.compile("\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(\\.\\d+)?[fFdDlLuU]*\\b")
    )

    // ── XML / HTML ──
    private val xmlRules = LanguageRules(
        name = "XML",
        keywords = setOf("xml", "html", "head", "body", "div", "span", "p", "a", "img"),
        lineComment = null,
        blockCommentStart = "<!--",
        blockCommentEnd = "-->",
        stringDelimiters = listOf("\"", "'"),
        numberPattern = null
    )

    // ── JSON ──
    private val jsonRules = LanguageRules(
        name = "JSON",
        keywords = setOf("true", "false", "null"),
        lineComment = null,
        blockCommentStart = null,
        blockCommentEnd = null,
        stringDelimiters = listOf("\""),
        numberPattern = Pattern.compile("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b")
    )

    // ── CSS ──
    private val cssRules = LanguageRules(
        name = "CSS",
        keywords = setOf(),
        lineComment = null,
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("\"", "'"),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|pt|cm|mm)?\\b")
    )

    // ── Shell ──
    private val shellRules = LanguageRules(
        name = "Shell",
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
            "case", "esac", "function", "return", "exit", "echo", "export",
            "source", "local", "readonly", "shift", "unset"
        ),
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        stringDelimiters = listOf("\"", "'"),
        numberPattern = null
    )

    // ── Ruby ──
    private val rubyRules = LanguageRules(
        name = "Ruby",
        keywords = setOf(
            "def", "class", "module", "if", "else", "elsif", "unless",
            "while", "until", "for", "do", "begin", "end", "rescue",
            "ensure", "raise", "return", "yield", "self", "true", "false",
            "nil", "require", "include", "extend", "attr_accessor",
            "attr_reader", "attr_writer", "private", "protected", "public",
            "new", "super"
        ),
        lineComment = "#",
        blockCommentStart = "=begin",
        blockCommentEnd = "=end",
        stringDelimiters = listOf("\"", "'"),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
    )

    // ── PHP ──
    private val phpRules = LanguageRules(
        name = "PHP",
        keywords = setOf(
            "function", "class", "interface", "trait", "extends", "implements",
            "public", "private", "protected", "static", "final", "abstract",
            "if", "else", "elseif", "switch", "case", "for", "foreach", "while",
            "do", "break", "continue", "return", "throw", "try", "catch",
            "finally", "new", "this", "self", "parent", "true", "false", "null",
            "echo", "print", "require", "include", "require_once", "include_once",
            "namespace", "use", "const", "define", "array", "list", "isset",
            "empty", "unset", "die", "exit", "global"
        ),
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("\"", "'"),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
    )

    // ── SQL ──
    private val sqlRules = LanguageRules(
        name = "SQL",
        keywords = setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE",
            "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AND", "OR",
            "NOT", "NULL", "IS", "IN", "LIKE", "BETWEEN", "ORDER", "BY",
            "GROUP", "HAVING", "LIMIT", "OFFSET", "AS", "DISTINCT", "COUNT",
            "SUM", "AVG", "MAX", "MIN", "EXISTS", "UNION", "ALL", "ANY",
            "CASE", "WHEN", "THEN", "ELSE", "END", "PRIMARY", "KEY",
            "FOREIGN", "REFERENCES", "CASCADE", "DEFAULT", "UNIQUE",
            "CHECK", "CONSTRAINT",
            // Also lowercase versions
            "select", "from", "where", "insert", "into", "values", "update",
            "set", "delete", "create", "table", "alter", "drop", "index",
            "join", "left", "right", "inner", "outer", "on", "and", "or",
            "not", "null", "is", "in", "like", "between", "order", "by",
            "group", "having", "limit", "offset", "as", "distinct", "count",
            "sum", "avg", "max", "min", "exists", "union", "all", "any",
            "case", "when", "then", "else", "end", "primary", "key",
            "foreign", "references", "cascade", "default", "unique",
            "check", "constraint"
        ),
        lineComment = "--",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        stringDelimiters = listOf("'", "\""),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
    )

    // ── Markdown ──
    private val markdownRules = LanguageRules(
        name = "Markdown",
        keywords = setOf(),
        lineComment = null,
        blockCommentStart = "<!--",
        blockCommentEnd = "-->",
        stringDelimiters = listOf(),
        numberPattern = null
    )

    // ── highlighting engine ──

    /**
     * Analyse [text] and return a list of span commands.
     * Designed to be called from a background coroutine.
     */
    fun analyse(text: CharSequence, rules: LanguageRules): List<SpanCommand> {
        val commands = mutableListOf<SpanCommand>()
        val len = text.length
        if (len == 0) return commands

        // Build a regex for keywords: \b(key1|key2|...)\b
        val keywordRegex = if (rules.keywords.isNotEmpty()) {
            val escaped = rules.keywords.map { Pattern.quote(it) }
            Pattern.compile("\\b(${escaped.joinToString("|")})\\b")
        } else null

        var i = 0
        while (i < len) {
            if (commands.size > 2000) break // safety limit

            // Block comments
            if (rules.blockCommentStart != null && rules.blockCommentEnd != null) {
                val bcs = rules.blockCommentStart
                val bce = rules.blockCommentEnd
                if (i + bcs.length <= len && text.subSequence(i, i + bcs.length).toString() == bcs) {
                    val end = text.indexOf(bce, i + bcs.length)
                    val commentEnd = if (end >= 0) end + bce.length else len
                    commands.add(SpanCommand(i, commentEnd, SpanType.COMMENT))
                    i = commentEnd
                    continue
                }
                // Python-style: block comment delimiter also used as string
                if (bcs == "\"\"\"" && rules.name == "Python") {
                    // Already handled above; skip falling through
                }
            }

            // Line comments
            if (rules.lineComment != null) {
                val lc = rules.lineComment
                if (lc == "#" && i < len && text[i] == '#') {
                    val end = indexOfNewline(text, i)
                    commands.add(SpanCommand(i, end, SpanType.COMMENT))
                    i = end
                    continue
                }
                if (lc.length == 2 && i + 1 < len &&
                    text[i] == lc[0] && text[i + 1] == lc[1]
                ) {
                    val end = indexOfNewline(text, i)
                    commands.add(SpanCommand(i, end, SpanType.COMMENT))
                    i = end
                    continue
                }
                // SQL line comment
                if (lc == "--" && i + 1 < len && text[i] == '-' && text[i + 1] == '-') {
                    val end = indexOfNewline(text, i)
                    commands.add(SpanCommand(i, end, SpanType.COMMENT))
                    i = end
                    continue
                }
            }

            // Strings
            var matched = false
            for (delim in rules.stringDelimiters) {
                val dlen = delim.length
                if (i + dlen <= len && text.subSequence(i, i + dlen).toString() == delim) {
                    // Find closing delimiter (simple approach: next unescaped delimiter)
                    var j = i + dlen
                    while (j < len) {
                        if (text[j] == '\\') { j += 2; continue }
                        if (j + dlen <= len &&
                            text.subSequence(j, j + dlen).toString() == delim
                        ) {
                            j += dlen
                            break
                        }
                        j++
                    }
                    val strEnd = minOf(j, len)
                    commands.add(SpanCommand(i, strEnd, SpanType.STRING))
                    i = strEnd
                    matched = true
                    break
                }
            }
            if (matched) continue

            // Annotations / decorators
            if (text[i] == '@' && i + 1 < len && text[i + 1].isLetter()) {
                var j = i + 1
                while (j < len && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == ':')) j++
                commands.add(SpanCommand(i, j, SpanType.ANNOTATION))
                i = j
                continue
            }

            // Numbers
            if (rules.numberPattern != null && text[i].isDigit()) {
                val m = rules.numberPattern.matcher(text.subSequence(i, len))
                if (m.find() && m.start() == 0) {
                    commands.add(SpanCommand(i, i + m.end(), SpanType.NUMBER))
                    i += m.end()
                    continue
                }
            }

            // Keywords
            if (keywordRegex != null && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val sub = text.subSequence(i, len)
                val m = keywordRegex.matcher(sub)
                if (m.find() && m.start() == 0) {
                    commands.add(SpanCommand(i, i + m.end(), SpanType.KEYWORD))
                    i += m.end()
                    continue
                }
            }

            i++
        }

        return commands
    }

    private fun indexOfNewline(text: CharSequence, start: Int): Int {
        for (i in start until text.length) {
            if (text[i] == '\n') return i
        }
        return text.length
    }
}
