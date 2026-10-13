package io.github.otoniel2004.mangareaderjp.data

import io.github.otoniel2004.mangareaderjp.srs.SrsState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val card = SavedItem(
        id = 42,
        type = ItemType.SENTENCE,
        text = "俺は絶対に諦めない",
        segments = listOf(Segment("俺", "おれ", PartOfSpeech.PRONOUN), Segment("は", null, PartOfSpeech.PARTICLE)),
        translation = "I'm never giving up!",
        source = "Reader",
        imagePath = "/data/user/0/app/files/card-images/x.jpg",
        createdAt = 1_000,
        srs = SrsState(dueAt = 5_000, intervalDays = 8, easeFactor = 2.35, repetitions = 3, lapses = 1, lastReviewedAt = 4_000),
    )

    @Test
    fun `round trip keeps the card and its study progress`() {
        val image = byteArrayOf(1, 2, 3, 4)
        val file = BackupFile(exportedAt = 9_000, items = listOf(BackupCodec.toBackup(card, image)))
        val restored = BackupCodec.decode(BackupCodec.encode(file)).items.single()

        val item = BackupCodec.toItem(restored, imagePath = "/new/path.jpg")
        assertEquals(card.copy(id = 0, imagePath = "/new/path.jpg"), item)
        assertArrayEquals(image, BackupCodec.imageBytes(restored))
    }

    @Test
    fun `cards without images have no image data`() {
        val backup = BackupCodec.toBackup(card.copy(imagePath = null), null)
        assertNull(backup.imageJpegBase64)
        assertNull(BackupCodec.imageBytes(backup))
    }

    @Test
    fun `other json files are rejected`() {
        val failure = runCatching { BackupCodec.decode("""{"format":"something-else","exportedAt":1,"items":[]}""") }
        assertTrue(failure.isFailure)
    }

    @Test
    fun `duplicate key ignores database ids and images`() {
        assertEquals(
            BackupCodec.key(card.type, card.text, card.createdAt),
            BackupCodec.key(ItemType.SENTENCE, "俺は絶対に諦めない", 1_000),
        )
    }
}
