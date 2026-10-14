package io.github.otoniel2004.yomitomo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otoniel2004.yomitomo.data.PartOfSpeech
import io.github.otoniel2004.yomitomo.data.Segment
import io.github.otoniel2004.yomitomo.japanese.JapaneseAnalyzer
import io.github.otoniel2004.yomitomo.japanese.Romaji
import io.github.otoniel2004.yomitomo.japanese.rubyParts

/**
 * Draws Japanese text with furigana (small reading on top).
 *
 * Each piece becomes a tiny column: reading on top, text below.
 * Columns are laid out in a FlowRow, which wraps when it runs out of space.
 * With furigana off, the top line disappears.
 *
 * colorByPart: paints each word with the color of its part of speech (noun, verb, particle…,
 * see [color]); particles are also bold.
 */
@Composable
fun FuriganaText(
    segments: List<Segment>,
    showFurigana: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    colorByPart: Boolean = false,
) {
    val rubySize = fontSize * 0.5f
    val baseColor = LocalContentColor.current

    // Every piece in order, so each one knows whether its neighbors carry a reading.
    val pieces = segments.flatMap { segment -> rubyParts(segment.surface, segment.reading).map { segment to it } }

    FlowRow(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        pieces.forEachIndexed { index, (segment, part) ->
            val pos = segment.displayPos
            val isParticle = colorByPart && pos == PartOfSpeech.PARTICLE
            val color = (if (colorByPart) pos.color() else null) ?: baseColor
            RubyColumn(
                overhang = fontSize,
                overhangLeft = pieces.getOrNull(index - 1)?.second?.ruby == null,
                overhangRight = pieces.getOrNull(index + 1)?.second?.ruby == null,
            ) {
                if (showFurigana) {
                    Text(
                        text = part.ruby ?: "",
                        fontSize = rubySize,
                        lineHeight = rubySize * 1.2f,
                        color = baseColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(
                    text = part.text,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.2f,
                    color = color,
                    fontWeight = if (isParticle) FontWeight.Bold else null,
                )
            }
        }
    }
}

/**
 * Reading on top, text below. Like real Japanese typesetting, a reading wider than its kanji
 * may overhang a neighbor by up to half of [overhang] — but only a neighbor without its own
 * reading (kana), so 諦(あきら)めない has no gap while 大賞投票 readings never collide.
 * Children: [ruby,] base.
 */
@Composable
private fun RubyColumn(
    overhang: TextUnit,
    overhangLeft: Boolean,
    overhangRight: Boolean,
    content: @Composable () -> Unit,
) {
    Layout(content) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val base = placeables.last()
        val ruby = placeables.getOrNull(placeables.size - 2)
        val half = overhang.roundToPx() / 2
        val left = if (overhangLeft) half else 0
        val right = if (overhangRight) half else 0
        val width = maxOf(base.width, (ruby?.width ?: 0) - left - right)
        // Where the reading starts: centered, then pushed back inside on a side that can't overhang.
        val rubyX = ruby?.let {
            ((width - it.width) / 2)
                .coerceAtLeast(if (overhangLeft) -half else 0)
                .coerceAtMost(width - it.width + if (overhangRight) half else 0)
        } ?: 0
        layout(width, placeables.sumOf { it.height }) {
            var y = 0
            for (placeable in placeables) {
                val x = if (placeable === ruby) rubyX else (width - placeable.width) / 2
                placeable.place(x, y)
                y += placeable.height
            }
        }
    }
}

/** Romaji line (reading in Latin letters) for a tokenized text. */
@Composable
fun RomajiLine(segments: List<Segment>, modifier: Modifier = Modifier) {
    Text(
        text = Romaji.fromSegments(segments),
        style = MaterialTheme.typography.bodyLarge,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Display name of each part of speech (used in the sentence breakdown). */
fun PartOfSpeech.label(): String = when (this) {
    PartOfSpeech.NOUN -> "noun"
    PartOfSpeech.PRONOUN -> "pronoun"
    PartOfSpeech.VERB -> "verb"
    PartOfSpeech.ADJECTIVE -> "adjective"
    PartOfSpeech.ADVERB -> "adverb"
    PartOfSpeech.PARTICLE -> "particle"
    PartOfSpeech.AUXILIARY -> "auxiliary"
    PartOfSpeech.CONJUNCTION -> "conjunction"
    PartOfSpeech.INTERJECTION -> "interjection"
    PartOfSpeech.PREFIX -> "prefix"
    PartOfSpeech.SUFFIX -> "suffix"
    PartOfSpeech.PUNCTUATION -> "punctuation"
    PartOfSpeech.OTHER -> "other"
}

/**
 * Part of speech to display. Cards saved before the punctuation fix may have "！" stored as
 * a noun, so symbols are always shown as punctuation.
 */
val Segment.displayPos: PartOfSpeech
    get() = if (JapaneseAnalyzer.isPunctuation(surface)) PartOfSpeech.PUNCTUATION else pos
