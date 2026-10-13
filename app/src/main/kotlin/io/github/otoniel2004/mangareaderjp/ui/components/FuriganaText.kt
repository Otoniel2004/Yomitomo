package io.github.otoniel2004.mangareaderjp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.japanese.Romaji
import io.github.otoniel2004.mangareaderjp.japanese.rubyParts

/**
 * Draws Japanese text with furigana (small reading on top).
 *
 * Each piece becomes a tiny column: reading on top, text below.
 * Columns are laid out in a FlowRow, which wraps when it runs out of space.
 * With furigana off, the top line disappears.
 *
 * highlightParticles: paints particles (は, が, を...) with the accent color.
 */
@Composable
fun FuriganaText(
    segments: List<Segment>,
    showFurigana: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    highlightParticles: Boolean = false,
) {
    val rubySize = fontSize * 0.5f
    val baseColor = LocalContentColor.current
    val particleColor = MaterialTheme.colorScheme.tertiary

    FlowRow(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (segment in segments) {
            val isParticle = highlightParticles && segment.pos == PartOfSpeech.PARTICLE
            val color = if (isParticle) particleColor else baseColor
            for (part in rubyParts(segment.surface, segment.reading)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
fun particleColor(): Color = MaterialTheme.colorScheme.tertiary
