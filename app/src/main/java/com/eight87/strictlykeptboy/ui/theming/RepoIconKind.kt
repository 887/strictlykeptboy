package com.eight87.strictlykeptboy.ui.theming

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image as FoundationImage
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Phase T.2 — sealed type for per-repo / per-calendar icons.
 *
 * Three open-closed variants. Each renders into a circular badge of a
 * caller-supplied size. New variants (e.g. species sticker, GitHub
 * avatar) can be added without modifying call sites — they pattern
 * match exhaustively on the sealed hierarchy.
 */
sealed interface RepoIconKind {
    /** Single-emoji icon (any user-typed unicode glyph). */
    data class Emoji(val glyph: String) : RepoIconKind

    /** SAF-picked photo URI (string form for Serializable persistence). */
    data class Photo(val uri: String) : RepoIconKind

    /**
     * 1-2 letter monogram on a tinted background. `seedColor` is the
     * hash-derived M3E tertiary-container substitute; `initials` should
     * be 1-2 visible characters.
     */
    data class AutoInitials(val initials: String, val seedColor: Color) : RepoIconKind

    /**
     * Species sticker — per D.88, the per-repo identity is rendered as a
     * species-graphic chosen during the wizard (Phase K.3). Until Phase WW
     * ships the full sticker bitmap pipeline, `species == "bat"` falls back
     * to `R.drawable.about_bat`; any other species falls back to
     * [AutoInitials] using the species name's first letter + a hash-derived
     * seed colour. When WW lands the resolver returns the real pack bitmap.
     */
    data class Sticker(val species: String) : RepoIconKind
}

/**
 * Stable seed-color derived from a name string. Maps the string hash
 * into the OKLab-ish hue circle; saturation + lightness picked to play
 * well in both light and dark M3E themes.
 *
 * Pure function — same input always returns same output. No randomness
 * across runs (so a repo's auto-icon never drifts).
 */
fun seedColorFromName(name: String): Color {
    if (name.isEmpty()) return Color(0xFF9E7BD8) // mascot purple fallback
    var h = 0
    for (c in name) h = (h * 31 + c.code) and 0x7FFFFFFF
    val hue = (h % 360).toFloat()
    return hueToColor(hue, saturation = 0.45f, lightness = 0.55f)
}

/** Derive the 1-2 character monogram from a display name. */
fun initialsFromName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "·"
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> (parts[0].first().uppercaseChar().toString() +
            parts[1].first().uppercaseChar()).take(2)
        else -> parts[0].first().uppercaseChar().toString()
    }
}

private fun hueToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - kotlin.math.abs(2 * lightness - 1f)) * saturation
    val hp = hue / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(red = (r1 + m).coerceIn(0f, 1f), green = (g1 + m).coerceIn(0f, 1f), blue = (b1 + m).coerceIn(0f, 1f))
}

const val TestTagRepoIcon = "RepoIcon"

/** Render the sealed icon variant inside a circular surface. */
@Composable
fun RepoIcon(
    kind: RepoIconKind,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(badgeBackground(kind))
            .testTag(TestTagRepoIcon),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            is RepoIconKind.Emoji -> Text(
                text = kind.glyph,
                fontSize = (sizeDp.value * 0.55f).sp,
            )
            is RepoIconKind.AutoInitials -> Text(
                text = kind.initials,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (sizeDp.value * 0.40f).sp,
            )
            is RepoIconKind.Photo -> PhotoBadge(kind.uri, sizeDp)
            is RepoIconKind.Sticker -> StickerBadge(kind.species, sizeDp)
        }
    }
}

@Composable
private fun badgeBackground(kind: RepoIconKind): Color = when (kind) {
    is RepoIconKind.AutoInitials -> kind.seedColor
    is RepoIconKind.Emoji -> MaterialTheme.colorScheme.surfaceContainerHighest
    is RepoIconKind.Photo -> MaterialTheme.colorScheme.surfaceContainerHighest
    // Sticker variants use the species-derived seed colour so the
    // initials-fallback for non-bat species reads consistent with the
    // standalone AutoInitials variant. Bat keeps the M3E surface tint
    // (matches the pre-D.88 IdentityAvatar look the user called "cute").
    is RepoIconKind.Sticker -> when (kind.species.lowercase()) {
        "bat" -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> seedColorFromName(kind.species)
    }
}

/**
 * Render a species sticker via the Phase WW [com.eight87.strictlykeptboy.avatar.AvatarResolver].
 *
 * Pulled from `LocalAvatarResolver`; falls back to a single-letter
 * monogram on the species seed colour when the resolver returns the
 * bat-fallback drawable for a non-bat species (artwork hasn't shipped
 * yet for the species and the bundled drawable is bat-themed).
 */
@Composable
private fun StickerBadge(species: String, sizeDp: Dp) {
    val resolver = com.eight87.strictlykeptboy.avatar.LocalAvatarResolver.current
    val resolved = remember(species, resolver) {
        resolver.resolve(species = species)
    }
    when (resolved) {
        is com.eight87.strictlykeptboy.avatar.AvatarResolver.Resolved.BitmapHit -> {
            Image(
                bitmap = resolved.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(sizeDp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        is com.eight87.strictlykeptboy.avatar.AvatarResolver.Resolved.DrawableFallback -> {
            if (species.lowercase() == "bat") {
                Image(
                    painter = androidx.compose.ui.res.painterResource(resolved.drawableRes),
                    contentDescription = null,
                    alignment = Alignment.Center,
                )
            } else {
                Text(
                    text = species.first().uppercaseChar().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (sizeDp.value * 0.40f).sp,
                )
            }
        }
    }
}

/**
 * Lightweight inline photo loader — we avoid pulling Coil into the
 * dep graph for a single use site. Bitmap decode happens on the IO
 * thread via LaunchedEffect; fallback shows the initial.
 */
@Composable
private fun PhotoBadge(uri: String, sizeDp: Dp) {
    val ctx = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = runCatching {
            val parsed = Uri.parse(uri)
            ctx.contentResolver.openInputStream(parsed)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    val b = bitmap
    if (b != null) {
        FoundationImage(
            bitmap = b,
            contentDescription = null,
            modifier = Modifier.size(sizeDp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Text("…", color = Color.White)
    }
}
