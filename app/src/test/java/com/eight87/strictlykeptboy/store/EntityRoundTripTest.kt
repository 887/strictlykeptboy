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
