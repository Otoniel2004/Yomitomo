package io.github.otoniel2004.mangareaderjp.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.otoniel2004.mangareaderjp.srs.SrsState

/**
 * A card on the home screen: a word, an expression or a whole sentence.
 *
 * - segments: the tokenized text (source of the furigana and the particle highlighting)
 * - meanings: dictionary meanings (word/expression)
 * - translation: full translation (sentence)
 * - context*: the manga sentence where the word was found
 * - imagePath: bubble crop (optional, from Phase 4 on)
 */
@Entity(tableName = "saved_items")
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val text: String,
    val segments: List<Segment>,
    val meanings: List<String> = emptyList(),
    val translation: String? = null,
    val contextSegments: List<Segment> = emptyList(),
    val contextTranslation: String? = null,
    val source: String? = null,
    val imagePath: String? = null,
    val showFurigana: Boolean = true,
    val createdAt: Long,
    @Embedded val srs: SrsState,
) {
    /** One-line summary used on the home cards. */
    val summary: String
        get() = translation ?: meanings.joinToString("; ")
}
