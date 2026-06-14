package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D-24 — "All lists" chip is present and clicking it resets the filter to null
 * (show all tasks across lists).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllListsChipTest {
    @get:Rule val composeRule = createComposeRule()

    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB

    @Test fun all_lists_chip_is_rendered() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskPerListView(
                        tasks = emptyList(),
                        todolists = listOf(a, b),
                        selectedListId = null,
                        onSelectList = {},
                        onToggleDone = {},
                        onOpen = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagListFilterChip-all").assertExists()
    }

    @Test fun clicking_all_lists_chip_calls_onSelectList_with_null() {
        var selected: String? = "a"
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskPerListView(
                        tasks = emptyList(),
                        todolists = listOf(a, b),
                        selectedListId = selected,
                        onSelectList = { selected = it },
                        onToggleDone = {},
                        onOpen = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagListFilterChip-all").performClick()
        assertNull("onSelectList(null) should reset filter to all lists", selected)
    }

    @Test fun per_list_chips_still_exist_alongside_all_chip() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskPerListView(
                        tasks = emptyList(),
                        todolists = listOf(a, b),
                        selectedListId = null,
                        onSelectList = {},
                        onToggleDone = {},
                        onOpen = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagListFilterChip-all").assertExists()
        composeRule.onNodeWithTag("$TestTagListFilterChip-a").assertExists()
        composeRule.onNodeWithTag("$TestTagListFilterChip-b").assertExists()
    }
}
