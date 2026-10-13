package io.github.otoniel2004.mangareaderjp.reader.ocr

/** A rectangle in capture pixels. Our own type so the filtering logic is plain Kotlin and unit-testable. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top

    /** Thickness of a text line: its short side (height for horizontal text, width for vertical). */
    val thickness get() = minOf(width, height)

    val isVertical get() = height > width

    fun union(other: Box) = Box(
        minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom),
    )

    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom

    fun expandedBy(margin: Int) = Box(left - margin, top - margin, right + margin, bottom + margin)

    fun scaled(factor: Float) = Box(
        (left * factor).toInt(), (top * factor).toInt(),
        (right * factor).toInt(), (bottom * factor).toInt(),
    )
}

/** One line (or, for vertical text, one column) recognized by the OCR. */
data class OcrLine(val text: String, val box: Box)

/** A group of lines the OCR considers one paragraph — in manga, usually one speech bubble. */
data class OcrBlock(val text: String, val box: Box, val lines: List<OcrLine>)
