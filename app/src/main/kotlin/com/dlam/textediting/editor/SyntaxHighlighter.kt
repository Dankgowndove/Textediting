package com.dlam.textediting.editor

import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

/**
 * 基于正则表达式的语法高亮引擎
 *
 * 支持 13 种编程/标记语言的语法着色，通过文件扩展名自动检测语言。
 *
 * ## 架构设计
 * - 所有分析工作在后台线程执行
 * - 结果以 [SpanCommand] 列表返回，调用方在主线程通过 [applyTo] 应用
 * - 语言检测基于文件扩展名（见 [detectLanguage]）
 *
 * ## 使用方式
 * ```kotlin
 * val rules = SyntaxHighlighter.detectLanguage("main.kt") ?: return
 * val commands = withContext(Dispatchers.Default) { SyntaxHighlighter.analyse(text, rules) }
 * SyntaxHighlighter.applyTo(spannable, commands, isDarkMode)
 * ```
 */
object SyntaxHighlighter {

    // ── 配色方案（ARGB 整数）──

    /** 亮色/暗色主题的语法高亮颜色定义 */
    object Colors {
        // Light theme（亮色主题）
        val LIGHT_KEYWORD = 0xFF0033B3.toInt()     // 关键字：深蓝
        val LIGHT_STRING = 0xFF067D17.toInt()       // 字符串：深绿
        val LIGHT_COMMENT = 0xFF8C8C8C.toInt()      // 注释：灰色
        val LIGHT_NUMBER = 0xFF1750EB.toInt()       // 数字：蓝色
        val LIGHT_ANNOTATION = 0xFFB07C00.toInt()   // 注解：金色

        // Dark theme（暗色主题）
        val DARK_KEYWORD = 0xFFCC7832.toInt()       // 关键字：橙色（IntelliJ 风格）
        val DARK_STRING = 0xFF6AAB73.toInt()        // 字符串：绿色
        val DARK_COMMENT = 0xFF808080.toInt()       // 注释：灰色
        val DARK_NUMBER = 0xFF6897BB.toInt()        // 数字：蓝色
        val DARK_ANNOTATION = 0xFFBBB529.toInt()    // 注解：黄色
    }

    /** Span 类型枚举 */
    enum class SpanType { KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION }

    /**
     * 高亮指令：描述一段文本应该用什么颜色高亮
     *
     * @property start 起始位置（包含）
     * @property end 结束位置（不包含）
     * @property type 高亮类型
     */
    data class SpanCommand(
        val start: Int,
        val end: Int,
        val type: SpanType
    )

    /**
     * 语言规则定义
     *
     * @property name 语言名称（用于调试）
     * @property keywords 关键字集合
     * @property lineComment 行注释前缀（如 //），null 表示不支持
     * @property blockCommentStart 块注释开始标记（如 /*），null 表示不支持
     * @property blockCommentEnd 块注释结束标记（如 */），null 表示不支持
     * @property stringDelimiters 字符串定界符列表（如 "、'、"""）
     * @property numberPattern 数字匹配正则，null 表示不匹配数字
     * @property keywordPattern 关键字正则（自动生成，通常为 null）
     */
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

    // ═══════════════════════════════════════════
    //  公开 API
    // ═══════════════════════════════════════════

    /**
     * 将高亮指令列表应用到 [Spannable]
     *
     * @param spannable 目标 Spannable 文本
     * @param commands 高亮指令列表
     * @param darkMode 是否使用暗色主题颜色
     */
    fun applyTo(spannable: Spannable, commands: List<SpanCommand>, darkMode: Boolean) {
        // 根据主题选择颜色
        val keywordColor = if (darkMode) Colors.DARK_KEYWORD else Colors.LIGHT_KEYWORD
        val stringColor = if (darkMode) Colors.DARK_STRING else Colors.LIGHT_STRING
        val commentColor = if (darkMode) Colors.DARK_COMMENT else Colors.LIGHT_COMMENT
        val numberColor = if (darkMode) Colors.DARK_NUMBER else Colors.LIGHT_NUMBER
        val annotationColor = if (darkMode) Colors.DARK_ANNOTATION else Colors.LIGHT_ANNOTATION

        for (cmd in commands) {
            // 边界检查，确保 start/end 在有效范围内
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

    /**
     * 清除 [Spannable] 上所有由语法高亮添加的 ForegroundColorSpan
     */
    fun clearSpans(spannable: Spannable) {
        val spans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        for (span in spans) {
            spannable.removeSpan(span)
        }
    }

    /**
     * 根据文件名检测编程语言
     *
     * @param fileName 文件名（仅使用扩展名部分）
     * @return 对应的 [LanguageRules]，如果不支持该语言则返回 null
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
            else -> null // 不支持的语言扩展名
        }
    }

    // ═══════════════════════════════════════════
    //  语言规则定义
    // ═══════════════════════════════════════════

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
        stringDelimiters = listOf("\"", "\"\"\""),  // 支持普通字符串和三引号字符串
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
        blockCommentStart = "\"\"\"",    // Python 三引号也是块注释
        blockCommentEnd = "\"\"\"",
        stringDelimiters = listOf("\"", "'", "\"\"\"", "'''"),
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[jJ]?\\b")  // 支持复数 j
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
        stringDelimiters = listOf("\"", "'", "`"),  // 支持模板字符串
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[eE][+-]?\\d+\\b|\\b\\d+(\\.\\d+)?\\b")
    )

    // ── C / C++ / Go / Rust（统一 C 风格）──
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
        lineComment = null,           // XML 无行注释
        blockCommentStart = "<!--",
        blockCommentEnd = "-->",
        stringDelimiters = listOf("\"", "'"),
        numberPattern = null          // XML 无数字高亮需求
    )

    // ── JSON ──
    private val jsonRules = LanguageRules(
        name = "JSON",
        keywords = setOf("true", "false", "null"),
        lineComment = null,           // JSON 标准不支持注释
        blockCommentStart = null,
        blockCommentEnd = null,
        stringDelimiters = listOf("\""),
        numberPattern = Pattern.compile("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b")
    )

    // ── CSS ──
    private val cssRules = LanguageRules(
        name = "CSS",
        keywords = setOf(),           // CSS 无关键字（选择器不是关键字）
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
        blockCommentStart = null,     // Shell 无块注释
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
        blockCommentStart = "=begin",  // Ruby 独特的块注释语法
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
            // 大写关键字
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE",
            "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AND", "OR",
            "NOT", "NULL", "IS", "IN", "LIKE", "BETWEEN", "ORDER", "BY",
            "GROUP", "HAVING", "LIMIT", "OFFSET", "AS", "DISTINCT", "COUNT",
            "SUM", "AVG", "MAX", "MIN", "EXISTS", "UNION", "ALL", "ANY",
            "CASE", "WHEN", "THEN", "ELSE", "END", "PRIMARY", "KEY",
            "FOREIGN", "REFERENCES", "CASCADE", "DEFAULT", "UNIQUE",
            "CHECK", "CONSTRAINT",
            // 小写关键字（同时支持大小写）
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
        keywords = setOf(),           // Markdown 无关键字高亮
        lineComment = null,
        blockCommentStart = "<!--",   // HTML 注释
        blockCommentEnd = "-->",
        stringDelimiters = listOf(),
        numberPattern = null
    )

    // ═══════════════════════════════════════════
    //  高亮分析引擎
    // ═══════════════════════════════════════════

    /**
     * 分析文本并返回高亮指令列表
     *
     * 设计为在后台协程中调用。使用单次遍历 + 优先级匹配策略：
     * 块注释 > 行注释 > 字符串 > 注解 > 数字 > 关键字
     *
     * @param text 待分析的文本
     * @param rules 语言规则
     * @return 高亮指令列表
     */
    fun analyse(text: CharSequence, rules: LanguageRules): List<SpanCommand> {
        val commands = mutableListOf<SpanCommand>()
        val len = text.length
        if (len == 0) return commands

        // 构建关键字正则：\b(key1|key2|...)\b
        val keywordRegex = if (rules.keywords.isNotEmpty()) {
            val escaped = rules.keywords.map { Pattern.quote(it) }
            Pattern.compile("\\b(${escaped.joinToString("|")})\\b")
        } else null

        var i = 0
        while (i < len) {
            // 安全上限：防止无限循环或过大文本消耗过多内存
            if (commands.size > 2000) break

            // 1. 匹配块注释（最高优先级）
            if (rules.blockCommentStart != null && rules.blockCommentEnd != null) {
                val bcs = rules.blockCommentStart
                val bce = rules.blockCommentEnd
                if (i + bcs.length <= len && text.subSequence(i, i + bcs.length).toString() == bcs) {
                    val end = text.indexOf(bce, i + bcs.length)
                    val commentEnd = if (end >= 0) end + bce.length else len  // 未闭合则到末尾
                    commands.add(SpanCommand(i, commentEnd, SpanType.COMMENT))
                    i = commentEnd
                    continue
                }
            }

            // 2. 匹配行注释
            if (rules.lineComment != null) {
                val lc = rules.lineComment
                // # 注释（Python, Ruby, Shell）
                if (lc == "#" && i < len && text[i] == '#') {
                    val end = indexOfNewline(text, i)
                    commands.add(SpanCommand(i, end, SpanType.COMMENT))
                    i = end
                    continue
                }
                // // 注释（Kotlin, Java, JS, C 系列, PHP）
                if (lc.length == 2 && i + 1 < len &&
                    text[i] == lc[0] && text[i + 1] == lc[1]
                ) {
                    val end = indexOfNewline(text, i)
                    commands.add(SpanCommand(i, end, SpanType.COMMENT))
                    i = end
                    continue
                }
                // -- SQL 行注释
                if (lc == "--" && i + 1 < len && text[i] == '-' && text[i + 1] == '-') {
                    val end = indexOfNewline(text, i)
                    commands.add(SpanCommand(i, end, SpanType.COMMENT))
                    i = end
                    continue
                }
            }

            // 3. 匹配字符串（处理转义字符 \）
            var matched = false
            for (delim in rules.stringDelimiters) {
                val dlen = delim.length
                if (i + dlen <= len && text.subSequence(i, i + dlen).toString() == delim) {
                    // 查找闭合定界符（简单方法：跳过转义字符，查找未转义的定界符）
                    var j = i + dlen
                    while (j < len) {
                        if (text[j] == '\\') { j += 2; continue }  // 跳过转义字符
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

            // 4. 匹配注解/装饰器（@xxx 模式）
            if (text[i] == '@' && i + 1 < len && text[i + 1].isLetter()) {
                var j = i + 1
                while (j < len && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == ':')) j++
                commands.add(SpanCommand(i, j, SpanType.ANNOTATION))
                i = j
                continue
            }

            // 5. 匹配数字
            if (rules.numberPattern != null && text[i].isDigit()) {
                val m = rules.numberPattern.matcher(text.subSequence(i, len))
                if (m.find() && m.start() == 0) {
                    commands.add(SpanCommand(i, i + m.end(), SpanType.NUMBER))
                    i += m.end()
                    continue
                }
            }

            // 6. 匹配关键字（确保前面不是字母/数字，避免匹配到标识符中间部分）
            if (keywordRegex != null && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val sub = text.subSequence(i, len)
                val m = keywordRegex.matcher(sub)
                if (m.find() && m.start() == 0) {
                    commands.add(SpanCommand(i, i + m.end(), SpanType.KEYWORD))
                    i += m.end()
                    continue
                }
            }

            // 无匹配，前进一个字符
            i++
        }

        return commands
    }

    /**
     * 查找从 start 位置开始的下一个换行符索引
     *
     * @return 换行符位置（不包含），如果没有找到则返回 text.length
     */
    private fun indexOfNewline(text: CharSequence, start: Int): Int {
        for (i in start until text.length) {
            if (text[i] == '\n') return i
        }
        return text.length
    }
}
