package com.eight87.strictlykeptboy.ui.theming

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val TestTagEventTitleEmojiPreview = "EventTitleEmojiPreview"

/**
 * Phase T.4 — strip a leading emoji from a title and return (emoji?, rest).
 *
 * The resolver persists the full title verbatim; the renderer surfaces
 * the leading emoji as a separate visual element when present so the
 * event-detail sheet can show a larger glyph next to the cleaner title
 * text. If the leading codepoint isn't an emoji-class character, the
 * function returns (null, original).
 */
fun splitLeadingEmoji(title: String): Pair<String?, String> {
    if (title.isEmpty()) return null to title
    val codePoint = title.codePointAt(0)
    val charCount = Character.charCount(codePoint)
    if (!isLikelyEmoji(codePoint)) return null to title
    val emoji = title.substring(0, charCount)
    val rest = title.substring(charCount).trimStart()
    return emoji to rest
}

private fun isLikelyEmoji(codePoint: Int): Boolean {
    // Pragmatic emoji-class detection: covers the Misc Symbols & Pictographs,
    // Supplemental Symbols & Pictographs, Transport & Map, Misc Symbols,
    // and Dingbats blocks that the brief's mascot set lives in. Variation
    // selectors and ZWJ sequences are *not* fully handled — for a v1
    // editor preview that's intentional; round-trip persistence happens
    // on the full string regardless.
    return when (codePoint) {
        in 0x1F300..0x1FAFF -> true // pictographs (incl. animal faces)
        in 0x1F600..0x1F64F -> true // emoticons
        in 0x2600..0x27BF -> true   // misc symbols, dingbats, sun, heart
        in 0x1F900..0x1F9FF -> true // supplemental symbols (faces, gestures)
        in 0x1F680..0x1F6FF -> true // transport
        in 0x2700..0x27BF -> true   // dingbats
        else -> false
    }
}

/**
 * Preview for event editor / event-detail sheet: leading emoji
 * rendered larger, the rest of the title in body style. Falls back to
 * a plain title row when no leading emoji is detected.
 */
@Composable
fun EventTitleEmojiPreview(title: String, modifier: Modifier = Modifier) {
    val (emoji, rest) = splitLeadingEmoji(title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag(TestTagEventTitleEmojiPreview),
    ) {
        if (emoji != null) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Text(rest, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        } else {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
