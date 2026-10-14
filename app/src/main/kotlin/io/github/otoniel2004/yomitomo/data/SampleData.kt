package io.github.otoniel2004.yomitomo.data

import io.github.otoniel2004.yomitomo.data.PartOfSpeech.ADJECTIVE
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.ADVERB
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.AUXILIARY
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.NOUN
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.PARTICLE
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.PRONOUN
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.PUNCTUATION
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.VERB
import io.github.otoniel2004.yomitomo.srs.Srs

/**
 * Samples inserted only on the first launch, so the home screen does not start empty.
 * From Phase 4 on, items come from the reader; these can be deleted like any other.
 */
object SampleData {
    fun items(now: Long): List<SavedItem> {
        fun item(
            type: ItemType,
            segments: List<Segment>,
            meanings: List<String> = emptyList(),
            translation: String? = null,
            context: List<Segment> = emptyList(),
            contextTranslation: String? = null,
            ageMinutes: Long,
        ) = SavedItem(
            type = type,
            text = segments.joinToString("") { it.surface },
            segments = segments,
            meanings = meanings,
            translation = translation,
            contextSegments = context,
            contextTranslation = contextTranslation,
            source = "Sample",
            createdAt = now - ageMinutes * Srs.MINUTE,
            srs = Srs.newCard(now),
        )

        return listOf(
            item(
                ItemType.WORD,
                listOf(Segment("約束", "やくそく", NOUN)),
                meanings = listOf("promise", "agreement", "appointment"),
                context = listOf(
                    Segment("約束", "やくそく", NOUN), Segment("を", null, PARTICLE),
                    Segment("守る", "まもる", VERB), Segment("よ", null, PARTICLE),
                    Segment("。", null, PUNCTUATION),
                ),
                contextTranslation = "I'll keep my promise.",
                ageMinutes = 60,
            ),
            item(
                ItemType.WORD,
                listOf(Segment("コーヒー", "こーひー", NOUN)),
                meanings = listOf("coffee"),
                ageMinutes = 50,
            ),
            item(
                ItemType.EXPRESSION,
                listOf(
                    Segment("仕方", "しかた", NOUN), Segment("が", null, PARTICLE),
                    Segment("ない", null, ADJECTIVE),
                ),
                meanings = listOf("it can't be helped", "there's nothing we can do"),
                ageMinutes = 40,
            ),
            item(
                ItemType.EXPRESSION,
                listOf(Segment("マジ", "まじ", NOUN), Segment("で", null, PARTICLE)),
                meanings = listOf("seriously", "for real (slang)"),
                ageMinutes = 30,
            ),
            item(
                ItemType.SENTENCE,
                listOf(
                    Segment("俺", "おれ", PRONOUN), Segment("は", null, PARTICLE),
                    Segment("絶対", "ぜったい", ADVERB), Segment("に", null, PARTICLE),
                    Segment("諦め", "あきらめ", VERB), Segment("ない", null, AUXILIARY),
                    Segment("！", null, PUNCTUATION),
                ),
                translation = "I'm never giving up!",
                ageMinutes = 20,
            ),
            item(
                ItemType.SENTENCE,
                listOf(
                    Segment("明日", "あした", NOUN), Segment("、", null, PUNCTUATION),
                    Segment("駅", "えき", NOUN), Segment("の", null, PARTICLE),
                    Segment("前", "まえ", NOUN), Segment("で", null, PARTICLE),
                    Segment("待っ", "まっ", VERB), Segment("て", null, PARTICLE),
                    Segment("る", null, AUXILIARY), Segment("ね", null, PARTICLE),
                    Segment("。", null, PUNCTUATION),
                ),
                translation = "I'll be waiting in front of the station tomorrow, okay?",
                ageMinutes = 10,
            ),
        )
    }
}
