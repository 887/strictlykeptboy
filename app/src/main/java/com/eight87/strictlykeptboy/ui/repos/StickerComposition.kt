package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.avatar.AvatarPackPrefs
import com.eight87.strictlykeptboy.avatar.AvatarResolver
import com.eight87.strictlykeptboy.avatar.CompositePackStore
import com.eight87.strictlykeptboy.avatar.LocalAvatarResolver
import com.eight87.strictlykeptboy.avatar.UserPackLoader

/**
 * Round 2.5.C — composition locals so [RepoSettingsScreen] can render a
 * "current sticker pack" summary without re-plumbing dependencies through
 * every layer. Default values are `null`; the host (MainActivity) wires
 * the live [AvatarPackPrefs] + [CompositePackStore] from `AppGraph`.
 */
val LocalAvatarPackPrefs = staticCompositionLocalOf<AvatarPackPrefs?> { null }
val LocalPackStore = staticCompositionLocalOf<CompositePackStore?> { null }
val LocalAssetPackLoader = staticCompositionLocalOf<AssetPackLoader?> { null }
val LocalUserPackLoader = staticCompositionLocalOf<UserPackLoader?> { null }

/**
 * Renders the active sticker for a species via the [AvatarResolver].
 * Drops back to a single-letter monogram when no bitmap is available
 * (e.g. bundled-pack manifest references artwork that hasn't shipped).
 */
@Composable
fun StickerThumbnail(species: String, sizeDp: Dp = 64.dp, modifier: Modifier = Modifier) {
    val resolver = LocalAvatarResolver.current
    val resolved = remember(species, resolver) { resolver.resolve(species = species) }
    Box(
        modifier = modifier.size(sizeDp).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (resolved) {
            is AvatarResolver.Resolved.BitmapHit -> {
                Image(
                    bitmap = resolved.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            is AvatarResolver.Resolved.DrawableFallback -> {
                if (species.lowercase() == "bat") {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(resolved.drawableRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = species.first().uppercaseChar().toString(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}
