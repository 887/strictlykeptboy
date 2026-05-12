package com.eight87.strictlykeptboy.ui.a11y

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.repos.AddRepoProvider
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.TopDestination
import com.eight87.strictlykeptboy.ui.tasks.TaskViewTab
import com.eight87.strictlykeptboy.ui.wizard.Alignment
import com.eight87.strictlykeptboy.ui.wizard.EmojiDensity
import com.eight87.strictlykeptboy.ui.wizard.Honorific
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice
import com.eight87.strictlykeptboy.ui.wizard.ToneRegister
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase U.4 — verifies every enum that previously hardcoded its label in
 * Kotlin code now exposes a `@StringRes labelRes: Int` resolving to a
 * non-empty English string. Closes F11.
 *
 * Strategy: enumerate `entries`, pull the `labelRes` getter, ask the
 * application context for the string. If any entry returns 0 (no resource)
 * or an empty string, the test fails — the compiler can't catch a missing
 * `when`-arm for a sealed hierarchy if someone deletes a case, but it can
 * catch a missing-string-resource at runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnumLabelLocalizationTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun top_destination_labels_resolve() {
        for (d in TopDestination.entries) {
            val res = d.labelRes
            check(res != 0) { "TopDestination.$d has no labelRes" }
            val s = ctx.getString(res)
            check(s.isNotEmpty()) { "TopDestination.$d resolves to empty string" }
            check(d.labelString(ctx) == s) { "context overload disagrees for $d" }
        }
    }

    @Test fun schedule_view_tab_labels_resolve() {
        for (t in ScheduleViewTab.entries) {
            val s = ctx.getString(t.labelRes)
            check(s.isNotEmpty()) { "ScheduleViewTab.$t empty" }
            check(t.labelString(ctx) == s)
        }
    }

    @Test fun task_view_tab_labels_resolve() {
        for (t in TaskViewTab.entries) {
            val s = ctx.getString(t.labelRes)
            check(s.isNotEmpty()) { "TaskViewTab.$t empty" }
            check(t.labelString(ctx) == s)
        }
    }

    @Test fun species_labels_resolve() {
        for (s in SpeciesChoice.entries) {
            val str = ctx.getString(s.labelRes)
            check(str.isNotEmpty()) { "SpeciesChoice.$s empty" }
            check(s.labelString(ctx) == str)
        }
    }

    @Test fun alignment_labels_and_taglines_resolve() {
        for (a in Alignment.entries) {
            check(ctx.getString(a.labelRes).isNotEmpty())
            check(ctx.getString(a.taglineRes).isNotEmpty())
            check(a.labelString(ctx).isNotEmpty())
        }
    }

    @Test fun role_labels_resolve() {
        for (r in RoleId.entries) {
            val s = ctx.getString(r.labelRes)
            check(s.isNotEmpty()) { "RoleId.$r empty" }
            check(r.labelString(ctx) == s)
        }
    }

    @Test fun honorific_labels_resolve() {
        for (h in Honorific.entries) {
            val s = ctx.getString(h.labelRes)
            check(s.isNotEmpty()) { "Honorific.$h empty" }
        }
    }

    @Test fun tone_register_labels_resolve() {
        for (t in ToneRegister.entries) {
            check(ctx.getString(t.labelRes).isNotEmpty()) { "ToneRegister.$t empty" }
        }
    }

    @Test fun emoji_density_labels_resolve() {
        for (e in EmojiDensity.entries) {
            check(ctx.getString(e.labelRes).isNotEmpty()) { "EmojiDensity.$e empty" }
        }
    }

    @Test fun add_repo_provider_labels_resolve() {
        for (p in AddRepoProvider.entries) {
            check(ctx.getString(p.labelRes).isNotEmpty()) { "AddRepoProvider.$p empty" }
            check(p.labelString(ctx) == ctx.getString(p.labelRes))
        }
    }

    /** Sample sanity-check: TopDestination.Schedule still says "Schedule" in default (English) locale. */
    @Test fun sample_english_strings() {
        check(ctx.getString(TopDestination.Schedule.labelRes) == "Schedule")
        check(ctx.getString(ScheduleViewTab.Day.labelRes) == "Day")
        check(ctx.getString(SpeciesChoice.Bat.labelRes) == "Bat")
        check(ctx.getString(R.string.locale_demo_color_seed) == "Color seed")
    }
}
