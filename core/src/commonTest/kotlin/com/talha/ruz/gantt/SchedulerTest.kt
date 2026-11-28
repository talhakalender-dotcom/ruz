package com.talha.ruz.gantt

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchedulerTest {
    private val tz = TimeZone.UTC
    private fun ms(d: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 11, d, h, m).toInstant(tz).toEpochMilliseconds()
    private fun task(id: String, d: Int, h: Int, dur: Int, allDay: Boolean = false) = SchedTask(id, Span(ms(d, h), dur), allDay)
    private fun link(a: String, b: String) = SchedLink(a, b)

    @Test fun successorSnapsToPredecessorEnd() {
        val tasks = listOf(task("kalip", 25, 9, 120), task("beton", 26, 14, 60))
        val r = Scheduler.propagate(tasks, listOf(link("kalip", "beton")), tz)
        assertEquals(Span(ms(25, 11), 60), r["beton"])
    }

    @Test fun chainMovesTogetherBothWays() {
        val links = listOf(link("a", "b"), link("b", "c"))
        val longer = listOf(task("a", 25, 9, 180), task("b", 25, 11, 60), task("c", 25, 12, 60))
        val r1 = Scheduler.propagate(longer, links, tz)
        assertEquals(ms(25, 12), r1["b"]?.startMs)
        assertEquals(ms(25, 13), r1["c"]?.startMs)
        // A kısaldı: B ve C geri gelir.
        val shorter = listOf(task("a", 25, 9, 60), task("b", 25, 11, 60), task("c", 25, 12, 60))
        val r2 = Scheduler.propagate(shorter, links, tz)
        assertEquals(ms(25, 10), r2["b"]?.startMs)
        assertEquals(ms(25, 11), r2["c"]?.startMs)
    }

    @Test fun multiplePredecessorsUseLatestEnd() {
        val tasks = listOf(task("a", 25, 9, 60), task("b", 25, 9, 240), task("c", 25, 8, 30))
        val r = Scheduler.propagate(tasks, listOf(link("a", "c"), link("b", "c")), tz)
        assertEquals(ms(25, 13), r["c"]?.startMs)
    }

    @Test fun unchangedTasksAreNotReported() {
        val tasks = listOf(task("a", 25, 9, 60), task("b", 25, 10, 60))
        assertTrue(Scheduler.propagate(tasks, listOf(link("a", "b")), tz).isEmpty())
    }

    @Test fun allDaySuccessorStartsNextDay() {
        val tasks = listOf(task("a", 25, 9, 120), task("b", 20, 0, 1440, allDay = true))
        assertEquals(ms(26, 0), Scheduler.propagate(tasks, listOf(link("a", "b")), tz)["b"]?.startMs)
        val afterAllDay = listOf(task("a", 25, 0, 1440, allDay = true), task("b", 20, 0, 1440, allDay = true))
        assertEquals(ms(26, 0), Scheduler.propagate(afterAllDay, listOf(link("a", "b")), tz)["b"]?.startMs)
    }

    @Test fun linksToUnknownTasksIgnored() {
        val tasks = listOf(task("b", 25, 10, 60))
        assertTrue(Scheduler.propagate(tasks, listOf(link("gone", "b")), tz).isEmpty())
    }

    @Test fun cycles() {
        val links = listOf(link("a", "b"), link("b", "c"))
        assertTrue(Scheduler.createsCycle(links, "c", "a"))
        assertTrue(Scheduler.createsCycle(links, "a", "a"))
        assertFalse(Scheduler.createsCycle(links, "a", "c"))
        assertFalse(Scheduler.createsCycle(links, "x", "a"))
    }

    @Test fun freeSlot() {
        val busy = listOf(Span(ms(25, 9), 60), Span(ms(25, 10), 90), Span(ms(25, 14), 60))
        assertEquals(ms(25, 11, 30), Scheduler.firstFreeSlot(busy, ms(25, 9), 60, ms(25, 22)))
        assertEquals(ms(25, 15), Scheduler.firstFreeSlot(busy, ms(25, 9), 180, ms(25, 22)))
        assertEquals(ms(25, 8), Scheduler.firstFreeSlot(busy, ms(25, 8), 60, ms(25, 22)))
        assertNull(Scheduler.firstFreeSlot(busy, ms(25, 21, 30), 60, ms(25, 22)))
    }
}
