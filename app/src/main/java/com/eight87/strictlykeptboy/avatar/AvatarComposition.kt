package com.eight87.strictlykeptboy.avatar

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Phase WW — composition-local handle so deep composables can pull the
 * [AvatarResolver] without prop-drilling through every nav level.
 *
 * Default is a no-op resolver returning the bat fallback — keeps
 * `@Preview` composables and unit tests running without explicit
 * provider wiring. Production set inside `MainActivity` from
 * `AppGraph.avatarResolver`.
 */
val LocalAvatarResolver = staticCompositionLocalOf<AvatarResolver> {
    NullAvatarResolver
}

/**
 * Default-value sentinel — used by Compose previews and tests that
 * don't bring up the full [com.eight87.strictlykeptboy.composition.AppGraph].
 * Always reports the bat fallback so call sites take the
 * `R.drawable.about_bat` branch.
 */
internal object NullAvatarResolver : AvatarResolver {
    override fun resolve(
        species: String,
        activityId: String?,
        neutralMode: Boolean,
    ): AvatarResolver.Resolved = AvatarResolver.Resolved.DrawableFallback(
        drawableRes = com.eight87.strictlykeptboy.R.drawable.about_bat,
        rung = StickerResolver.Rung.BatFallback,
    )
}
