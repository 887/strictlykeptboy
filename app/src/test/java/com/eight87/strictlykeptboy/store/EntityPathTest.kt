package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Test

class EntityPathTest {

    @Test fun eventBucketsByStartDate() {
        val p = EntityPath.event(
            calendarId = "cal-1",
            entityId = "ent-1",
            startDate = EntityPath.BucketDate.forIsoDateTime("2026-03-15T14:00:00+02:00"),
        )
        assertEquals("calendars/cal-1/events/2026/03/ent-1.md", p.toString().replace('\\', '/'))
    }

    @Test fun standingTaskHasNoBucket() {
        val p = EntityPath.standingTask("list-1", "task-1")
        assertEquals("todolists/list-1/standing/task-1.md", p.toString().replace('\\', '/'))
    }

    @Test fun recurrenceRuleNoBucket() {
        val p = EntityPath.recurrenceRule("cal-1", "rule-1")
        assertEquals("calendars/cal-1/recurrences/rule-1.md", p.toString().replace('\\', '/'))
    }

    @Test fun exceptionBucketByInstanceDate() {
        val p = EntityPath.exception("cal-1", "rule-1", "2026-05-11")
        assertEquals("calendars/cal-1/exceptions/rule-1/2026-05-11.md", p.toString().replace('\\', '/'))
    }
}
