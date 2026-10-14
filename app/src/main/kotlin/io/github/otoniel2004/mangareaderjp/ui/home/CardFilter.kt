package io.github.otoniel2004.mangareaderjp.ui.home

import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.japanese.JapaneseText
import io.github.otoniel2004.mangareaderjp.japanese.Romaji

/**
 * What the Home list shows:
 *   type   – word / expression / sentence (null = all)
 *   source – where the card came from: "Reader", "Manual", "Camera"… (null = all)
 *   query  – free text, matched against the Japanese, its reading, romaji, meanings and translation
 */
data class CardFilter(
    val type: ItemType? = null,
    val source: String? = null,
    val query: String = "",
) {
    fun matches(item: SavedItem): Boolean {
        if (type != null && item.type != type) return false
        if (source != null && item.source != source) return false
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val reading = item.segments.joinToString("") { it.reading ?: JapaneseText.toHiragana(it.surface) }
        val romaji = Romaji.fromSegments(item.segments)
        return listOf(item.text, reading, romaji, romaji.replace(" ", ""), item.summary)
            .any { it.lowercase().contains(needle) } ||
            JapaneseText.toHiragana(needle).let { kana -> reading.contains(kana) }
    }
}
