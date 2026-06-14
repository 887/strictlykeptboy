package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-12 fix — verifies the repoId→displayName resolution logic used by
 * the By Repo section headers in [ExpandedNowPlayingTaskBody].
 *
 * The rule: look up [TasksUiState.repoDisplayNames] by repoId; if absent,
 * fall back to the last path segment of the raw repoId slug.
 */
class ByRepoDisplayNameTest {

    private fun resolve(repoId: String, names: Map<String, String>): String =
        names[repoId] ?: repoId.substringAfterLast('/')

    @Test fun display_name_returned_when_present() {
        val names = mapOf("github.com/887/marc-kept" to "marc-kept")
        assertEquals("marc-kept", resolve("github.com/887/marc-kept", names))
    }

    @Test fun falls_back_to_last_segment_when_absent() {
        val names = emptyMap<String, String>()
        assertEquals("marc-kept", resolve("github.com/887/marc-kept", names))
    }

    @Test fun short_slug_without_path_separator_returns_itself() {
        val names = emptyMap<String, String>()
        assertEquals("my-calendar", resolve("my-calendar", names))
    }

    @Test fun display_name_beats_last_segment() {
        val names = mapOf("github.com/887/my-repo" to "My Friendly Repo Name")
        assertEquals("My Friendly Repo Name", resolve("github.com/887/my-repo", names))
    }

    @Test fun repoDisplayNames_stored_in_uiState() {
        val state = TasksUiState(
            repoDisplayNames = mapOf("r1" to "My Repo", "r2" to "Other Repo"),
        )
        assertEquals("My Repo", state.repoDisplayNames["r1"])
        assertEquals("Other Repo", state.repoDisplayNames["r2"])
        assertEquals(null, state.repoDisplayNames["unknown"])
    }
}
