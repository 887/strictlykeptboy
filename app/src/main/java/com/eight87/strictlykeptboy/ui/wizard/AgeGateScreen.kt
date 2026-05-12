package com.eight87.strictlykeptboy.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagAgeGate = "AgeGate"
const val TestTagAgeGateAccept = "AgeGate-Accept"
const val TestTagAgeGateDecline = "AgeGate-Decline"

/**
 * Phase K.14 — first-launch age gate (Mature 17+). Full-screen modal.
 * Decline exits the app gracefully; accept persists `age_confirmed_at`
 * and lets the wizard launch.
 */
@Composable
fun AgeGateScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Suppress back gesture — the user must confirm or decline.
    BackHandler { onDecline() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(TestTagAgeGate),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.about_bat),
            contentDescription = "bat mascot",
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Mature content — 17+",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This app contains references to adult lifestyle dynamics. You must be 17 or older to continue.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag(TestTagAgeGateDecline),
            ) { Text("Decline (exit)") }
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag(TestTagAgeGateAccept),
            ) { Text("I am 17 or older") }
        }
    }
}
