package com.eight87.strictlykeptboy.ui.theming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagCalColorPicker = "CalColorPicker"
const val TestTagCalColorSwatch = "CalColorSwatch"
const val TestTagCalColorHex = "CalColorHex"

/**
 * Phase T.3 — per-calendar color picker.
 *
 * 12-swatch M3E-friendly palette + optional HEX override. The seed Int
 * the caller persists is `Color.toArgb()` of the chosen color so a
 * downstream Compose / Room layer can re-instantiate without
 * re-deriving from the palette.
 */
@Composable
fun CalendarColorPicker(
    currentArgb: Int?,
    onSeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TestTagCalColorPicker),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.cal_color_picker_pick_swatch),
            style = MaterialTheme.typography.labelMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MaterialPalette) { color ->
                val argb = color.toArgb()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (currentArgb == argb)
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier,
                        )
                        .clickable { onSeedChange(argb) }
                        .testTag("$TestTagCalColorSwatch-${argb and 0x00FFFFFF}"),
                )
            }
        }

        var hex by remember(currentArgb) {
            mutableStateOf(currentArgb?.let { "#%06X".format(it and 0x00FFFFFF) } ?: "")
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = hex,
                onValueChange = { input ->
                    hex = input
                    parseHexToArgb(input)?.let(onSeedChange)
                },
                singleLine = true,
                label = { Text(stringResource(R.string.cal_color_picker_hex_label)) },
                modifier = Modifier.fillMaxWidth().testTag(TestTagCalColorHex),
            )
        }
    }
}

/** Parse `#RRGGBB` or `RRGGBB` into a fully-opaque ARGB int. */
fun parseHexToArgb(input: String): Int? {
    val s = input.trim().removePrefix("#")
    if (s.length != 6) return null
    return runCatching {
        val rgb = s.toInt(16)
        (0xFF shl 24) or rgb
    }.getOrNull()
}

private fun Color.toArgb(): Int {
    val a = (alpha * 255f + 0.5f).toInt()
    val r = (red * 255f + 0.5f).toInt()
    val g = (green * 255f + 0.5f).toInt()
    val b = (blue * 255f + 0.5f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Hand-picked M3E-derived 12-swatch palette. These map onto pastel
 * `tertiaryContainer` shades that play well at both 60% and 100%
 * lightness — the M3E dynamic-scheme generator can re-derive a full
 * scheme from any of these when used as a [colorSeed].
 */
val MaterialPalette: List<Color> = listOf(
    Color(0xFFE57373), // soft red
    Color(0xFFFFB74D), // orange
    Color(0xFFFFD54F), // amber
    Color(0xFFAED581), // lime
    Color(0xFF81C784), // green
    Color(0xFF4DD0E1), // teal
    Color(0xFF64B5F6), // blue
    Color(0xFF7986CB), // indigo
    Color(0xFFBA68C8), // purple
    Color(0xFFF06292), // pink
    Color(0xFFA1887F), // brown
    Color(0xFF90A4AE), // blue-grey
)
