package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerListTaskFilterTest {
    @get:Rule val composeRule = createComposeRule()

    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB

    @Test fun switching_lists_changes_visible_rows() {
        val tasks = listOf(
            TaskItem(id = "a1", title = "alpha-task", todolist = a),
            TaskItem(id = "a2", title = "alpha-task-2", todolist = a),
            TaskItem(id = "b1", title = "bravo-task", todolist = b),
        )

        var selected: String? = "a"
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskPerListView(
                        tasks = tasks,
                        todolists = listOf(a, b),
                        selectedListId = selected,
                        onSelectList = { selected = it },
                        onToggleDone = {},
                        onOpen = {},
                    )
                }
            }
        }

        // Both list filter chips render
        composeRule.onNodeWithTag("$TestTagListFilterChip-a").assertExists()
        composeRule.onNodeWithTag("$TestTagListFilterChip-b").assertExists()

        // Selected list A → only A rows visible
        composeRule.onNodeWithTag("$TestTagTaskRow-a1").assertExists()
        composeRule.onNodeWithTag("$TestTagTaskRow-a2").assertExists()
        composeRule.onNodeWithTag("$TestTagTaskRow-b1").assertDoesNotExist()
    }

    @Test fun null_selection_shows_all() {
        val tasks = listOf(
            TaskItem(id = "a1", title = "alpha", todolist = a),
            TaskItem(id = "b1", title = "bravo", todolist = b),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskPerListView(
                        tasks = tasks, todolists = listOf(a, b),
                        selectedListId = null,
                        onSelectList = {}, onToggleDone = {}, onOpen = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagTaskRow-a1").assertExists()
        composeRule.onNodeWithTag("$TestTagTaskRow-b1").assertExists()
    }
}

