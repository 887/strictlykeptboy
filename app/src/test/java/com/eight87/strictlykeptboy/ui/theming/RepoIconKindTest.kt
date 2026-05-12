package com.eight87.strictlykeptboy.ui.theming

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoIconKindTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `seed color stable for same input`() {
        val a = seedColorFromName("Personal Calendar")
        val b = seedColorFromName("Personal Calendar")
        assertEquals(a, b)
    }

    @Test fun `seed color differs across inputs`() {
        val a = seedColorFromName("Personal Calendar")
        val b = seedColorFromName("Work Repo")
        assertNotEquals(a, b)
    }

    @Test fun `seed color empty name has stable fallback`() {
        assertEquals(Color(0xFF9E7BD8), seedColorFromName(""))
    }

    @Test fun `initials derive single word`() {
        assertEquals("P", initialsFromName("Personal"))
    }

    @Test fun `initials derive two-word`() {
        assertEquals("PC", initialsFromName("personal calendar"))
    }

    @Test fun `initials empty string yields dot`() {
        assertEquals("·", initialsFromName(""))
    }

    @Test fun `each variant renders without crashing`() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(200.dp)) {
                    RepoIcon(RepoIconKind.Emoji("🦇"), sizeDp = 48.dp)
                }
            }
        }
        composeRule.onNodeWithTag(TestTagRepoIcon).assertIsDisplayed()
        composeRule.onNodeWithText("🦇").assertIsDisplayed()
    }

    @Test fun `auto initials renders monogram`() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(200.dp)) {
                    RepoIcon(
                        kind = RepoIconKind.AutoInitials("PC", seedColorFromName("Personal Calendar")),
                        sizeDp = 48.dp,
                    )
                }
            }
        }
        composeRule.onNodeWithText("PC").assertIsDisplayed()
    }
}
