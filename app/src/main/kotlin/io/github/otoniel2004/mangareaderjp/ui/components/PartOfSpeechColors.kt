package io.github.otoniel2004.mangareaderjp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.Segment

/**
 * One soft color per part of speech, so a sentence's structure is visible at a glance.
 * Light theme uses deeper tones, dark theme lighter ones; both stay readable as text.
 * null = normal text color (punctuation, unknown).
 */
@Composable
fun PartOfSpeech.color(): Color? {
    val dark = isSystemInDarkTheme()
    fun pick(light: Long, darkTone: Long) = Color(if (dark) darkTone else light)
    return when (this) {
        PartOfSpeech.NOUN -> pick(0xFF1565C0, 0xFF90CAF9)          // blue
        PartOfSpeech.PRONOUN -> pick(0xFF00838F, 0xFF80DEEA)       // teal
        PartOfSpeech.VERB -> pick(0xFF2E7D32, 0xFFA5D6A7)          // green
        PartOfSpeech.ADJECTIVE -> pick(0xFFE65100, 0xFFFFCC80)     // orange
        PartOfSpeech.ADVERB -> pick(0xFF9E7700, 0xFFFFF59D)        // yellow
        PartOfSpeech.PARTICLE -> pick(0xFFC2185B, 0xFFF8BBD0)      // pink
        PartOfSpeech.AUXILIARY -> pick(0xFF6A1B9A, 0xFFCE93D8)     // purple
        PartOfSpeech.PREFIX, PartOfSpeech.SUFFIX -> pick(0xFF5D4037, 0xFFBCAAA4) // brown
        PartOfSpeech.CONJUNCTION, PartOfSpeech.INTERJECTION -> pick(0xFFC62828, 0xFFEF9A9A) // red
        PartOfSpeech.PUNCTUATION, PartOfSpeech.OTHER -> null
    }
}

/** A small legend with the colors used in [segments] (only the parts of speech present). */
@Composable
fun PartOfSpeechLegend(segments: List<Segment>, modifier: Modifier = Modifier) {
    val present = segments.map { it.pos }.distinct().filter { it.color() != null }.sortedBy { it.ordinal }
    if (present.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        present.forEach { pos ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(pos.color() ?: Color.Unspecified))
                Spacer(Modifier.width(4.dp))
                Text(pos.label(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
