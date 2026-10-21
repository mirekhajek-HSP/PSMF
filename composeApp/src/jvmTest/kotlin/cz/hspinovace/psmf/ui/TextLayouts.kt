package cz.hspinovace.psmf.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/**
 * How the text of [this] node was laid out, which is what a test has to ask to
 * know whether a label fits. `assertIsDisplayed` is satisfied by a label that was
 * broken in the middle of a word, and a `lineCount` with `maxLines` set cannot
 * exceed it: the question is where the lines broke.
 */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.first()
}

/**
 * True when a line ends on an ordinal (`2.`) and the noun it belongs to starts the
 * next: "Розпочати 2." over "тайм". Allowed by a plain space and prevented by a
 * no-break one, which is what the strings use.
 */
fun TextLayoutResult.breaksAfterAnOrdinal(): Boolean {
    val text = layoutInput.text.text
    return (0 until lineCount - 1).any { line ->
        val end = getLineEnd(line, visibleEnd = false)
        end >= ORDINAL_AND_SPACE && text[end - 1].isWhitespace() && text[end - 2] == '.' && text[end - 3].isDigit()
    }
}

private const val ORDINAL_AND_SPACE = 3

/**
 * True when a line ends where a word does not: the text went on past the line's
 * last character with no space between them. A line that breaks after a space
 * is a line break; one that breaks after "Налаштуван" is a broken word.
 */
fun TextLayoutResult.breaksInsideAWord(): Boolean {
    val text = layoutInput.text.text
    return (0 until lineCount - 1).any { line ->
        val end = getLineEnd(line, visibleEnd = false)
        end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
    }
}
