package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.theming.RepoIcon
import com.eight87.strictlykeptboy.ui.theming.RepoIconKind

const val TestTagIdentityAvatar = "IdentityAvatar"

/**
 * Top-bar leading-slot avatar. Per D.88 / F48 the rendering reflects the
 * ACTIVE repo's identity — its [iconKind] (species sticker, photo, emoji,
 * or auto-initials). Tapping it routes to the repo switcher (the avatar IS
 * the active-repo affordance, not a separate user-account button).
 *
 * Default [iconKind] = [RepoIconKind.Sticker]("bat") preserves the
 * pre-D.88 behaviour for callers that haven't yet wired through the active
 * repo's config.
 */
@Composable
fun IdentityAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 28,
    iconKind: RepoIconKind = RepoIconKind.Sticker("bat"),
) {
    val cd = stringResource(R.string.cd_identity_avatar)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
            .testTag(TestTagIdentityAvatar),
    ) {
        RepoIcon(kind = iconKind, sizeDp = sizeDp.dp)
    }
}
