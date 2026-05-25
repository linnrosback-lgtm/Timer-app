package com.example.timerapp

import com.example.timerapp.util.formatMmSs
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun zeroMillisFormatsAsZeroZero() {
        assertEquals("00:00", formatMmSs(0L))
    }

    @Test
    fun oneSecondFormatsAsZeroOne() {
        assertEquals("00:01", formatMmSs(1_000L))
    }

    @Test
    fun oneMinuteFormatsAsOneZero() {
        assertEquals("01:00", formatMmSs(60_000L))
    }

    @Test
    fun fiveMinutesThirtySevenSeconds() {
        assertEquals("05:37", formatMmSs(5 * 60_000L + 37_000L))
    }

    @Test
    fun tenMinutesShowsTwoDigitMinutes() {
        assertEquals("10:00", formatMmSs(10 * 60_000L))
    }

    @Test
    fun ninetyNineMinutesFiftyNineSeconds() {
        assertEquals("99:59", formatMmSs(99 * 60_000L + 59_000L))
    }

    @Test
    fun negativeMillisClampsToZero() {
        assertEquals("00:00", formatMmSs(-5_000L))
    }

    @Test
    fun subSecondMillisTruncatesDown() {
        assertEquals("00:00", formatMmSs(999L))
        assertEquals("00:01", formatMmSs(1_999L))
    }
}
