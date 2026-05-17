package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Test

class EntityRoundTripTest {

    private val header = EntityHeader(
        id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001",
        createdAt = "2026-05-09T18:30:00+02:00",
        updatedAt = "2026-05-09T18:30:00+02:00",
        author = "01900000-0000-7000-8000-aaaaaaaaaaaa",
    )

    @Test fun eventRoundTrip() {
        val e = Event(
            header = header,
            title = "Dentist",
            start = "2026-05-12T14:00:00+02:00",
            end = "2026-05-12T14:45:00+02:00",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000001",
            location = "Köhler",
            tags = listOf("health", "dental"),
            emoji = "tooth",
            notifications = listOf("1d", "15m"),
            body = "bring x-ray\n",
        )
        val doc = e.toDoc()
        val text = FrontmatterWriter.serialize(doc)
        val parsed = FrontmatterReader.parse(text)
        val back = Event.fromDoc(parsed)
        assertEquals(e, back)
    }

    @Test fun eventGroupRoundTrip() {
        // Round 2.21.A.3 — `group` is preserved across serialize/parse.
        val e = Event(
            header = header,
            title = "Brush teeth",
            start = "2026-05-15T07:00:00+02:00",
            end = "2026-05-15T07:02:00+02:00",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000001",
            group = "Morning routine",
        )
        val back = Event.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(e.toDoc())))
        assertEquals("Morning routine", back.group)
        assertEquals(e, back)
    }

    @Test fun eventGroupNullByDefault() {
        val e = Event(
            header = header,
            title = "Dentist",
            start = "2026-05-12T14:00:00+02:00",
            end = "2026-05-12T14:45:00+02:00",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000001",
        )
        val back = Event.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(e.toDoc())))
        assertEquals(null, back.group)
    }

    @Test fun recurrenceGroupRoundTrip() {
        val r = RecurrenceRule(
            header = header,
            title = "Cardio",
            dtstart = "2026-01-05T17:00:00",
            duration = "PT30M",
            tzId = "Europe/Berlin",
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000020",
            group = "Workout",
        )
        val back = RecurrenceRule.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(r.toDoc())))
        assertEquals("Workout", back.group)
        assertEquals(r, back)
    }

    @Test fun recurrenceRoundTrip() {
        val r = RecurrenceRule(
            header = header,
            title = "Standup",
            dtstart = "2026-01-05T09:30:00",
            duration = "PT15M",
            tzId = "Europe/Berlin",
            rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000010",
            notifications = listOf("5m"),
            tags = listOf("work"),
            body = "daily standup\n",
        )
        val back = RecurrenceRule.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(r.toDoc())))
        assertEquals(r, back)
    }

    @Test fun exceptionCancelRoundTrip() {
        val x = Exception(
            header = header,
            ruleId = "0190d4ab-2b7a-7c50-9c1e-cccccccccccc",
            instanceDate = "2026-05-11",
            mode = "cancel",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000010",
            body = "holiday\n",
        )
        val back = Exception.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(x.toDoc())))
        assertEquals(x, back)
    }

    @Test fun taskRoundTrip() {
        val t = Task(
            header = header,
            title = "Buy milk",
            todolistId = "0190a0aa-2222-7000-8a0a-000000000022",
            due = "2026-05-09T18:30:00+02:00",
            done = false,
            priority = 450,
            tags = listOf("shopping"),
            body = "list:\n- [ ] milk\n",
        )
        val back = Task.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(t.toDoc())))
        assertEquals(t, back)
    }

    @Test fun standingTaskRoundTrip() {
        val s = StandingTask(
            header = header,
            title = "Sharpen knives",
            todolistId = "0190a0aa-2222-7000-8a0a-000000000022",
            pinned = true,
            tags = listOf("home"),
            body = "whetstone in drawer\n",
        )
        val back = StandingTask.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(s.toDoc())))
        assertEquals(s, back)
    }

    @Test fun deviationRoundTrip() {
        val d = Deviation(
            header = header,
            targetId = "cal-x:0190d4ab-2b7a-7c50-9c1e-cccccccccccc",
            instanceDate = "2026-05-11",
            devKind = "skipped",
            at = "2026-05-11T22:00:00+02:00",
            note = "tired",
        )
        val back = Deviation.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(d.toDoc())))
        assertEquals(d, back)
    }

    // --- Round 2.27.A.6 — keeper-prompt schema -------------------------------

    @Test fun eventPromptFieldsAbsentDefaultToNonPrompt() {
        // No prompt fields written; round-trip yields the documented defaults.
        val e = Event(
            header = header,
            title = "Dentist",
            start = "2026-05-12T14:00:00+02:00",
            end = "2026-05-12T14:45:00+02:00",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000001",
        )
        val text = FrontmatterWriter.serialize(e.toDoc())
        // Omit-on-default: the wire form must NOT contain the new keys.
        assert(!text.contains("requires_response")) { "requires_response leaked into default-valued event: $text" }
        assert(!text.contains("prompt_kind")) { "prompt_kind leaked into default-valued event: $text" }
        assert(!text.contains("prompt_target")) { "prompt_target leaked into default-valued event: $text" }
        val back = Event.fromDoc(FrontmatterReader.parse(text))
        assertEquals(false, back.requiresResponse)
        assertEquals(null, back.promptKind)
        assertEquals(null, back.promptTarget)
    }

    @Test fun eventPromptFieldsPresentRoundTrip() {
        val e = Event(
            header = header,
            title = "send proof you're still caged",
            start = "2026-05-18T10:30:00+02:00",
            end = "2026-05-18T10:35:00+02:00",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000001",
            requiresResponse = true,
            promptKind = PromptKind.Photo,
            promptTarget = PromptTarget.Keeper,
        )
        val back = Event.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(e.toDoc())))
        assertEquals(true, back.requiresResponse)
        assertEquals(PromptKind.Photo, back.promptKind)
        assertEquals(PromptTarget.Keeper, back.promptTarget)
        assertEquals(e, back)
    }

    @Test fun recurrencePromptFieldsPresentRoundTrip() {
        val r = RecurrenceRule(
            header = header,
            title = "send the keeper a cage photo",
            dtstart = "2026-05-17T11:00:00",
            duration = "PT5M",
            tzId = "Europe/Berlin",
            rrule = "FREQ=WEEKLY;BYDAY=SU",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000020",
            requiresResponse = true,
            promptKind = PromptKind.Photo,
            promptTarget = PromptTarget.Keeper,
        )
        val back = RecurrenceRule.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(r.toDoc())))
        assertEquals(true, back.requiresResponse)
        assertEquals(PromptKind.Photo, back.promptKind)
        assertEquals(PromptTarget.Keeper, back.promptTarget)
        assertEquals(r, back)
    }

    @Test fun recurrencePromptFieldsAbsentDefaults() {
        val r = RecurrenceRule(
            header = header,
            title = "Standup",
            dtstart = "2026-01-05T09:30:00",
            duration = "PT15M",
            tzId = "Europe/Berlin",
            rrule = "FREQ=WEEKLY;BYDAY=MO",
            calendarId = "0190a0aa-1c1d-7000-8a0a-000000000010",
        )
        val text = FrontmatterWriter.serialize(r.toDoc())
        assert(!text.contains("requires_response"))
        assert(!text.contains("prompt_kind"))
        assert(!text.contains("prompt_target"))
        val back = RecurrenceRule.fromDoc(FrontmatterReader.parse(text))
        assertEquals(false, back.requiresResponse)
        assertEquals(null, back.promptKind)
        assertEquals(null, back.promptTarget)
    }

    @Test fun promptEnumUnknownWireValuesFallBackGracefully() {
        // Synthesize a frontmatter blob with garbage enum values — these
        // are the kind of typos a user could leave in a hand-edited file.
        // PromptKind.fromToml / PromptTarget.fromToml must NOT throw and
        // must fall back to the most permissive defaults.
        val raw = """
            +++
            schema_version = 1
            id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
            kind = "event"
            created_at = 2026-05-09T18:30:00+02:00
            updated_at = 2026-05-09T18:30:00+02:00
            author = "01900000-0000-7000-8000-aaaaaaaaaaaa"
            title = "garbled prompt"
            start = 2026-05-12T14:00:00+02:00
            end = 2026-05-12T14:45:00+02:00
            calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000001"
            requires_response = true
            prompt_kind = "telegram"
            prompt_target = "moon"
            +++
        """.trimIndent()
        val parsed = FrontmatterReader.parse(raw)
        val back = Event.fromDoc(parsed)
        assertEquals(true, back.requiresResponse)
        assertEquals(PromptKind.CheckIn, back.promptKind)
        assertEquals(PromptTarget.Keeper, back.promptTarget)
    }

    @Test fun identityRoundTrip() {
        val i = Identity(
            header = header.copy(author = header.id),
            displayName = "Alex",
            email = "alex@example.com",
            defaultAuthor = true,
            pronouns = "he/him",
        )
        val back = Identity.fromDoc(FrontmatterReader.parse(FrontmatterWriter.serialize(i.toDoc())))
        assertEquals(i, back)
    }
}
