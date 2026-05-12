package com.eight87.strictlykeptboy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagIdentityAvatar = "IdentityAvatar"

/** UI-B.4 — identity avatar. Phase F stub: hardcoded bat sticker. Real identity
 *  loading lands in Phase K. */
@Composable
fun IdentityAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 28,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(TestTagIdentityAvatar),
    ) {
        Image(
            painter = painterResource(R.drawable.about_bat),
            contentDescription = stringResource(R.string.cd_identity_avatar),
            alignment = Alignment.Center,
        )
    }
}
