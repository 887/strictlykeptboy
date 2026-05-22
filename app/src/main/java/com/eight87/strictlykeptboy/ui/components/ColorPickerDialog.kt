package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

/**
 * Hand-rolled HSV colour picker, ported from tonearmboy's D.25.1 dialog
 * so the four boy-apps share one look-and-feel surface. A saturation/
 * value square (saturation = x, value = 1 - y) sits above a hue slider
 * with a preview swatch on top. Alpha is implicit 1; the caller stores
 * only the 24-bit RGB seed.
 *
 * Dependency-free on purpose — Material 3 doesn't ship a colour picker
 * and we don't want a third-party dep for ~120 lines of Compose.
 *
 * @param initialRgb seed value the picker shows on open, as `0xRRGGBB`.
 * @param onConfirm callback fired with the picked RGB long on Confirm.
 * @param onDismiss callback fired on Cancel or outside-tap dismiss.
 */
@Composable
fun ColorPickerDialog(
    initialRgb: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
) {
    val initialHsv = remember { rgbToHsv(initialRgb) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val previewColor = Color.hsv(hue, sat, value)
    val previewRgb = colorToRgbLong(previewColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_color_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("color_picker_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .testTag("color_picker_preview"),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "#%06X".format(previewRgb),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                SaturationValueSquare(
                    hue = hue,
                    saturation = sat,
                    value = value,
                    onSatValueChange = { newSat, newValue ->
                        sat = newSat
                        value = newValue
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("color_picker_sv_square"),
                )

                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .testTag("color_picker_hue_slider"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(previewRgb) },
                modifier = Modifier.testTag("color_picker_confirm"),
            ) { Text(stringResource(R.string.settings_color_picker_confirm)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onReset != null) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("color_picker_reset"),
                    ) { Text(stringResource(R.string.settings_color_picker_reset)) }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("color_picker_cancel"),
                ) { Text(stringResource(R.string.settings_color_picker_cancel)) }
            }
        },
    )
}

@Composable
private fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onSatValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val ringRadius = with(density) { 8.dp.toPx() }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0 && size.height > 0) {
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        onSatValueChange(s, v)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val pos = change.position
                    if (size.width > 0 && size.height > 0) {
                        val s = (pos.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        onSatValueChange(s, v)
                    }
                }
            },
    ) {
        size = this.size
        val pureHue = Color.hsv(hue, 1f, 1f)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, pureHue)),
            size = this.size,
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            size = this.size,
        )
        val cx = saturation * this.size.width
        val cy = (1f - value) * this.size.height
        drawCircle(color = Color.White, radius = ringRadius, center = Offset(cx, cy), style = Stroke(width = 4f))
        drawCircle(color = Color.Black, radius = ringRadius - 3f, center = Offset(cx, cy), style = Stroke(width = 2f))
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val indicatorWidth = with(density) { 4.dp.toPx() }

    val hueColors = remember {
        listOf(
            Color.hsv(0f, 1f, 1f),
            Color.hsv(60f, 1f, 1f),
            Color.hsv(120f, 1f, 1f),
            Color.hsv(180f, 1f, 1f),
            Color.hsv(240f, 1f, 1f),
            Color.hsv(300f, 1f, 1f),
            Color.hsv(360f, 1f, 1f),
        )
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (width > 0f) {
                        val h = (offset.x / width).coerceIn(0f, 1f) * 360f
                        onHueChange(h)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (width > 0f) {
                        val h = (change.position.x / width).coerceIn(0f, 1f) * 360f
                        onHueChange(h)
                    }
                }
            },
    ) {
        width = this.size.width
        drawRect(brush = Brush.horizontalGradient(hueColors), size = this.size)
        val x = (hue / 360f) * this.size.width
        drawRect(
            color = Color.White,
            topLeft = Offset(x - indicatorWidth / 2f, 0f),
            size = Size(indicatorWidth, this.size.height),
        )
        drawRect(
            color = Color.Black,
            topLeft = Offset(x - indicatorWidth / 2f, 0f),
            size = Size(indicatorWidth, this.size.height),
            style = Stroke(width = 1f),
        )
    }
}

/** 24-bit RGB long → `[hue, saturation, value]` (hue degrees, sat/value 0..1). */
internal fun rgbToHsv(rgb: Long): FloatArray {
    val r = ((rgb shr 16) and 0xFFL).toInt() / 255f
    val g = ((rgb shr 8) and 0xFFL).toInt() / 255f
    val b = (rgb and 0xFFL).toInt() / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h: Float = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    return floatArrayOf(h, s, max)
}

/** Compose [Color] → 24-bit `0xRRGGBB` long (alpha dropped, channels clamped). */
internal fun colorToRgbLong(color: Color): Long {
    val r = (color.red.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toLong()
    val g = (color.green.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toLong()
    val b = (color.blue.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toLong()
    return (r shl 16) or (g shl 8) or b
}
