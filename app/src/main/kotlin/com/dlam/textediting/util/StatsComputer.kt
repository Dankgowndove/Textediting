package com.dlam.textediting.util

import java.text.NumberFormat

/**
 * 文本统计结果数据类
 *
 * 所有数值字段已格式化为带千位分隔符的字符串（如 "1,234"），
 * 便于直接显示，无需额外格式化。
 *
 * @property totalLinesValue 原始行数值，用于判断文档大小
 */
data class StatsResult(
    val totalCharsWithSpace: String,   // 总字符数（含空格）
    val totalCharsNoSpace: String,     // 总字符数（不含空格）
    val totalLines: String,            // 总行数
    val nonEmptyLines: String,         // 非空行数
    val totalWords: String,            // 总词数
    val totalParagraphs: String,       // 段落数
    val chineseChars: String,          // 中文字符数
    val englishChars: String,          // 英文字符数
    val digitChars: String,            // 数字字符数
    val punctuationChars: String,      // 标点符号数
    val spaceChars: String,            // 空格/空白字符数
    val estimatedReadTime: String      // 估算阅读时间（如 "3 分 15 秒"）
) {
    /** 解析回整数行数，用于判断是否需要分段统计 */
    val totalLinesValue: Int
        get() = totalLines.replace(",", "").toIntOrNull() ?: 0
}

/**
 * 计算文本的详细统计信息
 *
 * 在后台线程（Dispatchers.Default）中调用以避免阻塞 UI。
 * 统计涵盖：字符计数、行/词/段落计数、中英文分类统计、阅读时间估算。
 *
 * @param text 待统计的文本内容
 * @return 格式化后的统计结果
 */
fun computeStats(text: String): StatsResult {
    // 数字格式化器（添加千位分隔符）
    val fmt = NumberFormat.getIntegerInstance()

    // ── 基础统计 ──
    val totalCharsWithSpace = text.length
    val totalCharsNoSpace = text.count { !it.isWhitespace() }

    // ── 行统计 ──
    val lines = text.lines()
    val totalLines = lines.size
    val nonEmptyLines = lines.count { it.isNotBlank() }

    // ── 词统计（按空白字符分割）──
    val totalWords = text.split(Regex("\\s+")).count { it.isNotEmpty() }

    // ── 段落统计（按连续空行分割）──
    val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }

    // ── 字符分类统计 ──
    // 中文字符：涵盖 CJK 统一表意文字基本区 + 扩展 A 区
    val chineseChars = text.count { it in '一'..'鿿' || it in '㐀'..'䶿' }
    // 英文字母：a-z + A-Z
    val englishChars = text.count { it in 'a'..'z' || it in 'A'..'Z' }
    // 数字字符：0-9
    val digitChars = text.count { it in '0'..'9' }
    // 标点符号：涵盖中英文常见标点共 20 种
    val punctSet = setOf(
        '，', '。', '、', '；', '：', '？', '！',  // 中文标点
        '.', ',', ';', ':', '?', '!',                                           // 英文标点
        '"', '\'', '(', ')', '（', '）',                                // 引号括号
        '【', '】', '《', '》', '<', '>',                       // 书名号括号
        '—', '…', '·'                                             // 破折号省略号间隔号
    )
    val punctuationChars = text.count { it in punctSet }
    // 空白字符（空格、制表符、换行等）
    val spaceChars = text.count { it.isWhitespace() }

    // ── 阅读时间估算 ──
    // 假设阅读速度：200 词/分钟，最少 0.1 分钟
    val readTimeMinutes = (totalWords / 200f).coerceAtLeast(0.1f)
    val readTimeStr = if (readTimeMinutes < 1) {
        "${(readTimeMinutes * 60).toInt()} 秒"           // 不足 1 分钟显示秒
    } else {
        "${readTimeMinutes.toInt()} 分 ${((readTimeMinutes % 1) * 60).toInt()} 秒"  // 显示分钟+秒
    }

    return StatsResult(
        totalCharsWithSpace = fmt.format(totalCharsWithSpace),
        totalCharsNoSpace = fmt.format(totalCharsNoSpace),
        totalLines = fmt.format(totalLines),
        nonEmptyLines = fmt.format(nonEmptyLines),
        totalWords = fmt.format(totalWords),
        totalParagraphs = fmt.format(paragraphs),
        chineseChars = fmt.format(chineseChars),
        englishChars = fmt.format(englishChars),
        digitChars = fmt.format(digitChars),
        punctuationChars = fmt.format(punctuationChars),
        spaceChars = fmt.format(spaceChars),
        estimatedReadTime = readTimeStr
    )
}
