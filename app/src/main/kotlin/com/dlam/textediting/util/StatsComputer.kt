package com.dlam.textediting.util

import java.text.NumberFormat

data class StatsResult(
    val totalCharsWithSpace: String,
    val totalCharsNoSpace: String,
    val totalLines: String,
    val nonEmptyLines: String,
    val totalWords: String,
    val totalParagraphs: String,
    val chineseChars: String,
    val englishChars: String,
    val digitChars: String,
    val punctuationChars: String,
    val spaceChars: String,
    val estimatedReadTime: String
) {
    val totalLinesValue: Int
        get() = totalLines.replace(",", "").toIntOrNull() ?: 0
}

fun computeStats(text: String): StatsResult {
    val fmt = NumberFormat.getIntegerInstance()
    val totalCharsWithSpace = text.length
    val totalCharsNoSpace = text.count { !it.isWhitespace() }
    val lines = text.lines()
    val totalLines = lines.size
    val nonEmptyLines = lines.count { it.isNotBlank() }
    val totalWords = text.split(Regex("\\s+")).count { it.isNotEmpty() }
    val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }

    val chineseChars = text.count { it in '一'..'鿿' || it in '㐀'..'䶿' }
    val englishChars = text.count { it in 'a'..'z' || it in 'A'..'Z' }
    val digitChars = text.count { it in '0'..'9' }
    val punctSet = setOf(
        '，', '。', '、', '；', '：', '？', '！', '.', ',', ';', ':', '?', '!',
        '"', '\'', '(', ')', '（', '）', '【', '】', '《', '》', '<', '>', '—', '…', '·'
    )
    val punctuationChars = text.count { it in punctSet }
    val spaceChars = text.count { it.isWhitespace() }

    val readTimeMinutes = (totalWords / 200f).coerceAtLeast(0.1f)
    val readTimeStr = if (readTimeMinutes < 1) {
        "${(readTimeMinutes * 60).toInt()} 秒"
    } else {
        "${readTimeMinutes.toInt()} 分 ${((readTimeMinutes % 1) * 60).toInt()} 秒"
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
