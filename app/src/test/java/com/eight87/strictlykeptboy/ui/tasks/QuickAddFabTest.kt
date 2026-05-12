package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddFabTest {
    @get:Rule val composeRule = createComposeRule()

    private val a = TaskFixtures.listA
    private val b = TaskFixtures.listB

    @Test fun fab_renders_and_clicks() {
        var clicked = false
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskQuickAddFab(onClick = { clicked = true })
                }
            }
        }
        composeRule.onNodeWithTag(TestTagQuickAddFab).performClick()
        assertEquals(true, clicked)
    }

    @Test fun sheet_submit_emits_request_with_chosen_target() {
        var submitted: TaskQuickAddRequest? = null

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskQuickAddSheet(
                        todolists = listOf(a, b),
                        initialTarget = QuickAddTarget.Todolist(a),
                        onDismiss = {},
                        onSubmit = { title, target ->
                            submitted = TaskQuickAddRequest(title, target)
                        },
                    )
                }
            }
        }

        // Default target is list a; switch to list b.
        composeRule.onNodeWithTag("$TestTagQuickAddTarget-list-b").performClick()
        composeRule.onNodeWithTag(TestTagQuickAddInput).performTextInput("Buy spinach")
        composeRule.onNodeWithTag(TestTagQuickAddSubmit).performClick()

        assertNotNull(submitted)
        assertEquals("Buy spinach", submitted!!.title)
        assertEquals(QuickAddTarget.Todolist(b), submitted!!.target)
    }
}
