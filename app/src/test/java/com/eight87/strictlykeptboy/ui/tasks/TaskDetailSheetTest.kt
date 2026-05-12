package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDetailSheetTest {
    @get:Rule val composeRule = createComposeRule()

    private val a = TaskFixtures.listA
    private val today = TaskFixtures.today

    @Test fun renders_all_fields_including_subtasks_and_attachments() {
        val task = TaskItem(
            id = "x", title = "Long task",
            todolist = a,
            due = today,
            priority = 5,
            tags = listOf("urgent", "phone"),
            author = "alex",
            body = "do the thing\n- [ ] sub-a\n- [x] sub-b",
            attachments = listOf(
                TaskAttachment(AttachmentKind.Link, "tracking", "https://example.com"),
            ),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Surface {
                    TaskDetailSheet(
                        task = task,
                        onDismiss = {},
                        onEdit = {},
                        onToggleDone = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag(TestTagDetailSheet).assertExists()
        composeRule.onNodeWithTag(TestTagDetailTitle).assertExists()
        composeRule.onNodeWithText("Long task").assertExists()
        composeRule.onNodeWithTag(TestTagDetailList).assertExists()
        composeRule.onNodeWithTag(TestTagDetailAuthor).assertExists()
        composeRule.onNodeWithTag(TestTagDetailEdit).assertExists()
        // Subtasks
        composeRule.onNodeWithTag("$TestTagDetailSubtask-0").assertExists()
        composeRule.onNodeWithTag("$TestTagDetailSubtask-1").assertExists()
        // Attachment
        composeRule.onNodeWithTag("$TestTagDetailAttachment-0").assertExists()
        composeRule.onNodeWithText("#urgent").assertExists()
    }
}
