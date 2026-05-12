package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingModeTest {
    @get:Rule val composeRule = createComposeRule()

    private val shop = TaskFixtures.shop

    @Test fun renders_big_checkboxes_and_no_due_chrome() {
        val tasks = listOf(
            TaskItem(id = "milk", title = "Milk", todolist = shop, priority = 5,
                due = TaskFixtures.today),
            TaskItem(id = "bread", title = "Bread", todolist = shop, priority = 5,
                due = TaskFixtures.today.minusDays(1)),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskShoppingView(tasks = tasks, onToggleDone = {})
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagShoppingRow-milk").assertExists()
        composeRule.onNodeWithTag("$TestTagShoppingCheckbox-milk").assertExists()
        // No due chip rendered in shopping view, regardless of `due`.
        composeRule.onNodeWithTag("$TestTagDueChip-milk").assertDoesNotExist()
        composeRule.onNodeWithTag("$TestTagDueChip-bread").assertDoesNotExist()
    }

    @Test fun tap_row_toggles_done() {
        var toggled: String? = null
        val tasks = listOf(TaskItem(id = "eggs", title = "Eggs", todolist = shop))
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskShoppingView(tasks = tasks, onToggleDone = { toggled = it.id })
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagShoppingRow-eggs").performClick()
        assertTrue("expected onToggleDone fired", toggled == "eggs")
    }
}
