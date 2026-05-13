package com.eight87.strictlykeptboy.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.store.ReviewResponseWriter

/**
 * Phase DDD.13 / UI-SS — Reviews surface.
 *
 * Dom-side: lists unreviewed `reviewable_change` entries grouped by date
 * with reaction-strip + free-text composer.
 *
 * Boy-side: response-on-my-commits view (rendered by the same panel with
 * `side = Boy`).
 *
 * SOLID:
 *  - **S:** Just renders + collects user input. The dom-side write goes
 *    through [onSubmitResponse] which the caller wires to
 *    [ReviewResponseWriter].
 *  - **I:** [ReviewEntry] is the narrowest contract that drives the
 *    composables — no DB / no Git API leaks here.
 */
const val TestTagReviewsPane = "ReviewsPane"
const val TestTagReviewsEmpty = "ReviewsEmpty"
const val TestTagReviewItemPrefix = "ReviewItem-"
const val TestTagReviewReactionPrefix = "ReviewReaction-"
const val TestTagReviewComposerInput = "ReviewComposerInput"
const val TestTagReviewSubmit = "ReviewSubmit"

enum class ReviewsSide { Dom, Boy }
enum class ReviewsFilter { All, Unread, ReactionsOnly, Threaded }

data class ReviewEntry(
    val commitSha: String,
    val author: String,
    val timestamp: String,
    val autoSummary: String,
    val unread: Boolean,
    /** Responses recorded against this commit (boy-side feed). */
    val responses: List<RenderedResponse> = emptyList(),
)

data class RenderedResponse(
    val responderLabel: String,
    val reactions: List<String>,
    val body: String,
    val cuteCodedLgtm: Boolean,
)

@Composable
fun ReviewsPane(
    side: ReviewsSide,
    items: List<ReviewEntry>,
    boyHonorific: String = "Sir",
    boyPraiseTerm: String = "good boy",
    onSubmitResponse: (commitSha: String, reactions: List<String>, body: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(ReviewsFilter.All) }
    val filtered = remember(items, filter) {
        when (filter) {
            ReviewsFilter.All -> items
            ReviewsFilter.Unread -> items.filter { it.unread }
            ReviewsFilter.ReactionsOnly -> items.filter { e -> e.responses.any { it.reactions.isNotEmpty() } }
            ReviewsFilter.Threaded -> items.filter { it.responses.size > 1 }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp).testTag(TestTagReviewsPane),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReviewsFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { filter = f },
                    label = { Text(f.name) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag(TestTagReviewsEmpty),
            ) {
                Text(
                    text = when (side) {
                        ReviewsSide.Dom -> "No new reviews from $boyPraiseTerm yet."
                        ReviewsSide.Boy -> "$boyHonorific hasn't responded yet — give them time."
                    },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.commitSha }) { entry ->
                    ReviewItem(
                        side = side,
                        entry = entry,
                        onSubmit = { reactions, body -> onSubmitResponse(entry.commitSha, reactions, body) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(
    side: ReviewsSide,
    entry: ReviewEntry,
    onSubmit: (List<String>, String) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .testTag("$TestTagReviewItemPrefix${entry.commitSha}"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = entry.author,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (entry.unread) {
                    Spacer(Modifier.padding(start = 6.dp))
                    AssistChip(onClick = {}, label = { Text("unread") })
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(entry.timestamp, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Text(entry.autoSummary, style = MaterialTheme.typography.bodyMedium)
            // Existing responses
            if (entry.responses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                entry.responses.forEach { r ->
                    ResponseBubble(r)
                }
            }
            if (side == ReviewsSide.Dom) {
                Spacer(Modifier.height(12.dp))
                ResponseComposer(onSubmit = onSubmit)
            }
        }
    }
}

@Composable
private fun ResponseBubble(r: RenderedResponse) {
    Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(
                text = r.responderLabel + if (r.cuteCodedLgtm) " — LGTM 🦇" else "",
                style = MaterialTheme.typography.labelMedium,
            )
            if (r.reactions.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(r.reactions.joinToString(" "), style = MaterialTheme.typography.bodySmall)
            }
            if (r.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(r.body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResponseComposer(onSubmit: (List<String>, String) -> Unit) {
    var picked by remember { mutableStateOf(setOf<String>()) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        // 9-token reaction picker per UI-SS.2.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReviewResponseWriter.REACTIONS.forEach { r ->
                FilterChip(
                    selected = r in picked,
                    onClick = {
                        picked = if (r in picked) picked - r else picked + r
                    },
                    label = { Text(r) },
                    modifier = Modifier.testTag("$TestTagReviewReactionPrefix$r"),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().testTag(TestTagReviewComposerInput),
            placeholder = { Text("Markdown response (optional)") },
        )
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = {
                onSubmit(picked.toList(), text)
                picked = emptySet()
                text = ""
            },
            enabled = picked.isNotEmpty() || text.isNotBlank(),
            modifier = Modifier.testTag(TestTagReviewSubmit),
        ) {
            Text("Send")
        }
    }
}
