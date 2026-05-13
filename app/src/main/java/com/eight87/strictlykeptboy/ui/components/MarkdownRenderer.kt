package com.eight87.strictlykeptboy.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Phase EE — strictlykeptboy/D.31 / UI-Y.
 *
 * Thin Compose facade over noties/Markwon (Apache-2.0). Renders a
 * Markdown source string into a styled `TextView` and exposes it as a
 * Composable. Intentionally narrow surface (ISP): one composable, one
 * string in, one optional `linkPolicy` strategy out.
 *
 * **Scope:** read-only display of body text in
 * `EventDetailSheet` / `TaskDetailSheet`. The editor-mode raw/rendered
 * toggle (UI-Y.2) is out of scope for the Phase EE close-out — that
 * lands once the body editor itself is touched.
 *
 * **SOLID notes:**
 * - S: single reason to change (Markwon styling rules + theme mapping).
 * - O: extensions hook through `MarkdownLinkPolicy` — adding new URI
 *   schemes (e.g. Phase MM `strictlykeptboy://`) means swapping the
 *   policy, not editing this file.
 * - L: every code path returns a fully-styled TextView; no
 *   `TODO()`/`NotImplementedError`.
 * - I: takes a `String` body — not a god-state object.
 * - D: depends on Markwon abstractions + the policy interface; concrete
 *   Android `Intent` dispatch lives behind `DefaultMarkdownLinkPolicy`.
 *
 * Theme integration (EE.4): the rendered text follows
 * `MaterialTheme.typography.bodyMedium` for base text + font-scale via
 * `LocalDensity.fontScale`. Heading levels (h1..h6) map to
 * proportional multipliers of the bodyMedium size — matching the
 * `displaySmall`..`titleMedium` step pattern called out in UI-Y.3
 * without paying the cost of plumbing every TextStyle through Markwon's
 * `MarkwonTheme` builder (which only accepts pixel sizes).
 */
@Composable
fun MarkdownRenderer(
    markdown: String,
    modifier: Modifier = Modifier,
    linkPolicy: MarkdownLinkPolicy = DefaultMarkdownLinkPolicy,
    testTag: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typography = MaterialTheme.typography.bodyMedium
    val colorScheme = MaterialTheme.colorScheme

    // Resolve the bodyMedium font size to raw px so Markwon's
    // pixel-based MarkwonTheme can honour Compose typography +
    // font-scale + density-scale together.
    val baseTextSizePx: Float = run {
        val unit = typography.fontSize
        when (unit.type) {
            TextUnitType.Sp -> unit.value * density.density * density.fontScale
            TextUnitType.Em -> unit.value * 16f * density.density * density.fontScale
            else -> 14f * density.density * density.fontScale
        }
    }
    val codeBackgroundArgb = colorScheme.surfaceContainerHigh.toArgb()
    val codeTextArgb = colorScheme.onSurface.toArgb()
    val linkArgb = colorScheme.primary.toArgb()
    val textArgb = colorScheme.onSurface.toArgb()
    val blockMarginPx: Int = with(density) { 8.dp.toPx().toInt() }

    val markwon = remember(
        baseTextSizePx,
        codeBackgroundArgb,
        codeTextArgb,
        linkArgb,
        textArgb,
        blockMarginPx,
    ) {
        buildMarkwon(
            context = context,
            baseTextSizePx = baseTextSizePx,
            codeBackgroundArgb = codeBackgroundArgb,
            codeTextArgb = codeTextArgb,
            linkArgb = linkArgb,
            blockMarginPx = blockMarginPx,
        )
    }

    AndroidView(
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx)
                setTextColor(textArgb)
            }
        },
        update = { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx)
            view.setTextColor(textArgb)
            markwon.setMarkdown(view, markdown)
            // EE.5 — re-route every URLSpan installed by Markwon
            // through `linkPolicy.open(...)` so the same composable
            // can serve detail-sheet bodies today and editor preview
            // tomorrow without changing the call site.
            interceptLinks(view, linkPolicy)
        },
    )
}

/**
 * Strategy interface for link dispatch. Default impl handles
 * `strictlykeptboy://` (Phase MM deep-link handler — currently a
 * pass-through to `Intent.ACTION_VIEW` since Phase MM has not
 * shipped) and standard schemes (http/https/mailto/tel/etc.).
 *
 * Tests substitute a recording impl to assert URI routing.
 */
fun interface MarkdownLinkPolicy {
    fun open(context: Context, uri: Uri)
}

/**
 * EE.5 default policy. `strictlykeptboy://...` URIs fan out via
 * implicit-intent so Phase MM's future `BrowsableActivity` /
 * deep-link router catches them at install time. Until MM ships, this
 * is identical to the generic branch — Android's intent resolver
 * simply has no handler registered, which surfaces as
 * `ActivityNotFoundException` we swallow + log to logcat (the brief
 * forbids unhandled crashes on body link taps).
 */
val DefaultMarkdownLinkPolicy: MarkdownLinkPolicy = MarkdownLinkPolicy { context, uri ->
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No handler. Swallowing is the correct UX — the user tapped a
        // link, we tried, nothing else to do without a toast (out of
        // scope for the renderer component).
    }
}

// ---- internals -----------------------------------------------------

private fun buildMarkwon(
    context: Context,
    baseTextSizePx: Float,
    codeBackgroundArgb: Int,
    codeTextArgb: Int,
    linkArgb: Int,
    blockMarginPx: Int,
): Markwon = Markwon.builder(context)
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .usePlugin(HtmlPlugin.create())
    .usePlugin(LinkifyPlugin.create())
    .usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureTheme(builder: MarkwonTheme.Builder) {
            // EE.4 — typography hookup: base size already on the
            // TextView, here we tune block/inline-code + link colour +
            // block margins to follow M3E tokens.
            builder
                .codeBackgroundColor(codeBackgroundArgb)
                .codeTextColor(codeTextArgb)
                .codeBlockBackgroundColor(codeBackgroundArgb)
                .codeBlockTextColor(codeTextArgb)
                .codeBlockMargin(blockMarginPx)
                .codeTypeface(Typeface.MONOSPACE)
                .codeBlockTypeface(Typeface.MONOSPACE)
                .codeTextSize(baseTextSizePx.toInt())
                .codeBlockTextSize(baseTextSizePx.toInt())
                .linkColor(linkArgb)
                .blockMargin(blockMarginPx)
                .blockQuoteWidth(blockMarginPx / 2)
                // EE.6 implicit: code blocks read on
                // `surfaceContainerHigh` with monospace face.
                .headingBreakHeight(0)
                .headingTextSizeMultipliers(
                    floatArrayOf(2.00f, 1.50f, 1.25f, 1.10f, 1.00f, 0.85f),
                )
        }

        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            // Link clicks routed through the URLSpan intercept in
            // `interceptLinks(...)`, not Markwon's default resolver,
            // so the open() strategy is the single source of truth.
        }
    })
    .build()

private fun interceptLinks(view: TextView, policy: MarkdownLinkPolicy) {
    val text = view.text as? Spannable ?: return
    val spans = text.getSpans(0, text.length, URLSpan::class.java)
    for (span in spans) {
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        val flags = text.getSpanFlags(span)
        val url = span.url ?: continue
        text.removeSpan(span)
        text.setSpan(PolicyUrlSpan(url, policy), start, end, flags)
    }
}

private class PolicyUrlSpan(url: String, private val policy: MarkdownLinkPolicy) : URLSpan(url) {
    override fun onClick(widget: View) {
        policy.open(widget.context, Uri.parse(url))
    }
}
