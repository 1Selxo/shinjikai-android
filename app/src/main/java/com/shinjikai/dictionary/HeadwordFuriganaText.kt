package com.shinjikai.dictionary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.shinjikai.dictionary.data.WritingPart
import kotlin.math.max

@Composable
internal fun HeadwordFuriganaText(
    text: String,
    reading: String,
    parts: List<WritingPart>?,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    rubyStyle: TextStyle = MaterialTheme.typography.labelMedium,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    rubyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign = TextAlign.End
) {
    val cleanText = text.trim()
    if (cleanText.isBlank()) return

    val segments = remember(cleanText, reading, parts) {
        buildHeadwordFuriganaSegments(
            text = cleanText,
            reading = reading.trim(),
            parts = parts.orEmpty()
        )
    }
    if (segments.none { !it.ruby.isNullOrBlank() }) {
        Text(
            text = cleanText,
            modifier = modifier,
            style = baseStyle.tightRubyStyle().copy(textDirection = TextDirection.ContentOrLtr),
            color = baseColor,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    FuriganaLayout(
        segments = segments,
        modifier = modifier.fillMaxWidth(),
        baseStyle = baseStyle.tightRubyStyle().copy(textDirection = TextDirection.ContentOrLtr),
        rubyStyle = rubyStyle.tightRubyStyle().copy(textDirection = TextDirection.ContentOrLtr),
        baseColor = baseColor,
        rubyColor = rubyColor,
        textAlign = textAlign
    )
}

@Composable
private fun FuriganaLayout(
    segments: List<HeadwordRubySegment>,
    modifier: Modifier,
    baseStyle: TextStyle,
    rubyStyle: TextStyle,
    baseColor: Color,
    rubyColor: Color,
    textAlign: TextAlign
) {
    val rowGapPx = with(LocalDensity.current) { 5.dp.roundToPx() }
    val maxRubyOverlapPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Layout(
            modifier = modifier,
            content = {
                segments.forEach { segment ->
                    Text(
                        text = segment.ruby.orEmpty(),
                        style = rubyStyle,
                        color = rubyColor,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                    Text(
                        text = segment.base,
                        style = baseStyle,
                        color = baseColor,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val measured = segments.mapIndexed { index, segment ->
                val ruby = measurables[index * 2].measure(looseConstraints)
                val base = measurables[index * 2 + 1].measure(looseConstraints)
                FuriganaMeasuredSegment(
                    segment = segment,
                    ruby = ruby,
                    base = base,
                    hasRuby = !segment.ruby.isNullOrBlank(),
                    width = max(ruby.width, base.width)
                )
            }

            val availableWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                measured.sumOf { it.width }.coerceAtLeast(constraints.minWidth)
            }.coerceAtLeast(1)

            val rows = mutableListOf<List<FuriganaMeasuredSegment>>()
            var currentRow = mutableListOf<FuriganaMeasuredSegment>()
            var currentWidth = 0
            measured.forEach { item ->
                if (currentRow.isNotEmpty() && currentWidth + item.width > availableWidth) {
                    rows += currentRow
                    currentRow = mutableListOf()
                    currentWidth = 0
                }
                currentRow += item
                currentWidth += item.width
            }
            if (currentRow.isNotEmpty()) rows += currentRow

            data class PlacedItem(
                val item: FuriganaMeasuredSegment,
                val x: Int,
                val rowY: Int,
                val rubyLaneHeight: Int,
                val rubyBlockHeight: Int
            )

            val placed = mutableListOf<PlacedItem>()
            var measuredHeight = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowWidth = row.sumOf { it.width }
                var x = when (textAlign) {
                    TextAlign.Center -> (availableWidth - rowWidth) / 2
                    TextAlign.Right, TextAlign.End -> availableWidth - rowWidth
                    else -> 0
                }.coerceAtLeast(0)
                val rubyLaneHeight = row.maxOfOrNull { if (it.hasRuby) it.ruby.height else 0 } ?: 0
                val baseLaneHeight = row.maxOfOrNull { it.base.height } ?: 0
                val rubyOverlap = ((rubyLaneHeight * 92) / 100).coerceAtMost(maxRubyOverlapPx)
                val rubyBlockHeight = (rubyLaneHeight - rubyOverlap).coerceAtLeast(0)
                row.forEach { item ->
                    placed += PlacedItem(
                        item = item,
                        x = x,
                        rowY = measuredHeight,
                        rubyLaneHeight = rubyLaneHeight,
                        rubyBlockHeight = rubyBlockHeight
                    )
                    x += item.width
                }
                measuredHeight += rubyBlockHeight + baseLaneHeight
                if (rowIndex != rows.lastIndex) measuredHeight += rowGapPx
            }

            val layoutWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                rows.maxOfOrNull { row -> row.sumOf { it.width } } ?: 0
            }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val layoutHeight = measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

            layout(layoutWidth, layoutHeight) {
                placed.forEach { placedItem ->
                    val item = placedItem.item
                    val baseX = placedItem.x + ((item.width - item.base.width) / 2)
                    val baseY = placedItem.rowY + placedItem.rubyBlockHeight
                    if (item.hasRuby) {
                        val rubyX = placedItem.x + ((item.width - item.ruby.width) / 2)
                        val rubyY = placedItem.rowY +
                            (placedItem.rubyLaneHeight - item.ruby.height).coerceAtLeast(0)
                        item.ruby.placeRelative(rubyX, rubyY)
                    }
                    item.base.placeRelative(baseX, baseY)
                }
            }
        }
    }
}

private data class HeadwordRubySegment(
    val base: String,
    val ruby: String?
)

private data class FuriganaMeasuredSegment(
    val segment: HeadwordRubySegment,
    val ruby: Placeable,
    val base: Placeable,
    val hasRuby: Boolean,
    val width: Int
)

private data class HeadwordRubyToken(
    val base: String,
    val kanji: Boolean,
    val readingHint: String? = null
)

private fun buildHeadwordFuriganaSegments(
    text: String,
    reading: String,
    parts: List<WritingPart>
): List<HeadwordRubySegment> {
    buildSegmentsFromWritingParts(text, parts)?.let { return it }
    val tokens = tokenizeHeadword(text)
    val readings = alignHeadwordReadings(tokens, reading).orEmpty()
    return tokens.mapIndexed { index, token ->
        HeadwordRubySegment(
            base = token.base,
            ruby = readings.getOrNull(index)
                ?.takeIf { token.kanji && it.isNotBlank() && it != token.base }
        )
    }
}

private fun buildSegmentsFromWritingParts(
    text: String,
    parts: List<WritingPart>
): List<HeadwordRubySegment>? {
    if (parts.isEmpty()) return null
    val segments = mutableListOf<HeadwordRubySegment>()
    var partIndex = 0
    var textIndex = 0
    var matched = false
    while (textIndex < text.length) {
        val codePoint = Character.codePointAt(text, textIndex)
        val charText = String(Character.toChars(codePoint))
        val part = parts.getOrNull(partIndex)
        val ruby = if (
            part != null &&
            part.kanji == codePoint &&
            part.reading.isNotBlank()
        ) {
            matched = true
            partIndex += 1
            part.reading.trim()
        } else {
            null
        }
        segments += HeadwordRubySegment(
            base = charText,
            ruby = ruby?.takeIf { isHeadwordKanjiCodePoint(codePoint) && it != charText }
        )
        textIndex += Character.charCount(codePoint)
    }
    return segments.takeIf { matched }
}

private fun tokenizeHeadword(text: String): List<HeadwordRubyToken> {
    val tokens = mutableListOf<HeadwordRubyToken>()
    var index = 0
    while (index < text.length) {
        val start = index
        val kanji = isHeadwordKanji(text[index])
        if (kanji) {
            while (index < text.length && isHeadwordKanji(text[index])) index += 1
        } else {
            index += 1
            while (index < text.length && !isHeadwordKanji(text[index])) index += 1
        }
        tokens += HeadwordRubyToken(
            base = text.substring(start, index),
            kanji = kanji
        )
    }
    return tokens
}

private fun alignHeadwordReadings(
    tokens: List<HeadwordRubyToken>,
    reading: String
): List<String?>? {
    if (reading.isBlank()) return tokens.map { null as String? }
    val memo = mutableMapOf<Pair<Int, Int>, List<String?>?>()

    fun solve(tokenIndex: Int, readingIndex: Int): List<String?>? {
        val key = tokenIndex to readingIndex
        if (key in memo) return memo[key]
        if (tokenIndex >= tokens.size) {
            val result = if (readingIndex == reading.length) emptyList<String?>() else null
            memo[key] = result
            return result
        }

        val token = tokens[tokenIndex]
        val result = if (token.kanji) {
            var matched: List<String?>? = null
            for (end in reading.length downTo (readingIndex + 1)) {
                val tail = solve(tokenIndex + 1, end) ?: continue
                matched = listOf(reading.substring(readingIndex, end)) + tail
                break
            }
            matched
        } else {
            val consumed = consumeHeadwordLiteral(token.base, reading, readingIndex)
            if (consumed >= 0) {
                solve(tokenIndex + 1, readingIndex + consumed)?.let { listOf(null) + it }
            } else {
                null
            }
        }
        memo[key] = result
        return result
    }

    return solve(0, 0) ?: alignHeadwordReadingsGreedy(tokens, reading)
}

private fun alignHeadwordReadingsGreedy(
    tokens: List<HeadwordRubyToken>,
    reading: String
): List<String?> {
    val readings = MutableList<String?>(tokens.size) { null }
    var readingIndex = 0
    tokens.forEachIndexed { index, token ->
        if (token.kanji) {
            val nextLiteral = tokens.drop(index + 1)
                .firstOrNull { !it.kanji }
                ?.base
                ?.firstOrNull()
            val end = nextLiteral
                ?.let { literal ->
                    (reading.length - 1 downTo readingIndex)
                        .firstOrNull { headwordLiteralEquals(literal, reading[it]) }
                }
                ?: reading.length
            val safeEnd = end.coerceIn(readingIndex, reading.length)
            readings[index] = reading.substring(readingIndex, safeEnd)
            readingIndex = safeEnd
        } else {
            val consumed = consumeHeadwordLiteral(token.base, reading, readingIndex)
            if (consumed >= 0) readingIndex += consumed
        }
    }
    return readings
}

private fun consumeHeadwordLiteral(base: String, reading: String, start: Int): Int {
    var readingIndex = start
    base.forEach { char ->
        when {
            char.isWhitespace() -> Unit
            readingIndex < reading.length && headwordLiteralEquals(char, reading[readingIndex]) -> readingIndex += 1
            else -> return -1
        }
    }
    return readingIndex - start
}

private fun isHeadwordKanji(char: Char): Boolean {
    return isHeadwordKanjiCodePoint(char.code)
}

private fun isHeadwordKanjiCodePoint(codePoint: Int): Boolean {
    val block = Character.UnicodeBlock.of(codePoint)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        codePoint == '々'.code ||
        codePoint == '〆'.code
}

private fun headwordLiteralEquals(textChar: Char, readingChar: Char): Boolean {
    return textChar == readingChar || normalizeHeadwordLiteral(textChar) == normalizeHeadwordLiteral(readingChar)
}

private fun normalizeHeadwordLiteral(char: Char): Char {
    return when (char) {
        'ー', 'ｰ' -> 'ー'
        '。', '.' -> '。'
        '、', ',' -> '、'
        else -> char
    }
}

@Suppress("DEPRECATION")
private fun TextStyle.tightRubyStyle(): TextStyle {
    return copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
}
