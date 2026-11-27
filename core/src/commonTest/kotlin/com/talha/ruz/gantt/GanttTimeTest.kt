package com.talha.ruz.gantt

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GanttTimeTest {
    private val tz = TimeZone.UTC
    private fun ms(d: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 11, d, h, m).toInstant(tz).toEpochMilliseconds()
    private val min = 60_000.0

    @Test fun scaleRoundTrip() {
        val s = TimeScale(ms(25, 0).toDouble(), 2f)
        assertEquals(120f, s.x(ms(25, 1)))
        assertEquals(ms(25, 1).toDouble(), s.time(120f))
    }

    @Test fun panMovesBackInTime() {
        val s = TimeScale(ms(25, 10).toDouble(), 1f).pan(60f)
        assertEquals(ms(25, 9).toDouble(), s.viewStartMs)
    }

    @Test fun zoomKeepsAnchor() {
        val s = TimeScale(ms(25, 0).toDouble(), 1f)
        val anchor = 600f
        val z = s.zoomAround(anchor, 2f, 0.1f, 10f)
        assertEquals(s.time(anchor), z.time(anchor), 1.0)
        assertEquals(2f, z.pxPerMin)
        assertEquals(10f, s.zoomAround(anchor, 100f, 0.1f, 10f).pxPerMin)
    }

    @Test fun snapStepByZoom() {
        assertEquals(15, snapStepMinutes(2f, 24f))
        assertEquals(60, snapStepMinutes(0.5f, 24f))
        assertEquals(1440, snapStepMinutes(0.1f, 24f))
    }

    @Test fun snapping() {
        assertEquals(ms(25, 14, 15), snapToStep(ms(25, 14, 22), 15, tz))
        assertEquals(ms(25, 14, 30), snapToStep(ms(25, 14, 23), 15, tz))
        assertEquals(ms(25, 15), snapToStep(ms(25, 14, 31), 60, tz))
        assertEquals(ms(26, 0), snapToStep(ms(25, 13), 1440, tz))
        assertEquals(ms(25, 0), snapToStep(ms(25, 11), 1440, tz))
    }

    @Test fun labelStep() {
        assertEquals(60, hourLabelStepMinutes(1f, 40f))
        assertEquals(15, hourLabelStepMinutes(4f, 40f))
        assertNull(hourLabelStepMinutes(0.02f, 40f))
    }

    @Test fun dragMove() {
        val s = Span(ms(25, 14), 60)
        assertEquals(Span(ms(25, 15, 30), 60), applyDrag(s, DragHandle.Move, 92 * min, 15, tz))
    }

    @Test fun dragEndExtendsAndNeverInverts() {
        val s = Span(ms(25, 14), 60)
        assertEquals(Span(ms(25, 14), 120), applyDrag(s, DragHandle.End, 58 * min, 15, tz))
        assertEquals(Span(ms(25, 14), 15), applyDrag(s, DragHandle.End, -300 * min, 15, tz))
    }

    @Test fun dragStartKeepsEnd() {
        val s = Span(ms(25, 14), 60)
        assertEquals(Span(ms(25, 13), 120), applyDrag(s, DragHandle.Start, -61 * min, 15, tz))
        assertEquals(Span(ms(25, 14, 45), 15), applyDrag(s, DragHandle.Start, 300 * min, 15, tz))
    }

    @Test fun allDayDragByDays() {
        val s = Span(ms(25, 0), 1440)
        assertEquals(Span(ms(27, 0), 1440), applyDrag(s, DragHandle.Move, 2 * 1440 * min + 100 * min, 1440, tz))
        assertEquals(Span(ms(25, 0), 2880), applyDrag(s, DragHandle.End, 1000 * min, 1440, tz))
    }
}
