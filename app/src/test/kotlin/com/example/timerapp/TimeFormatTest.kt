package com.example.timerapp

import com.example.timerapp.util.formatMmSs
import com.example.timerapp.util.formatClockTime
import com.example.timerapp.util.formatCountdown
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

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

class FormatClockTimeTest {
    @Test
    fun formatClockTimeReturnsHhMm() {
        // Use a known epoch offset: 1970-01-01 14:54 UTC
        val millis = (14 * 3600 + 54 * 60) * 1_000L
        // Force UTC so test is timezone-independent
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("14:54", formatClockTime(millis))
        } finally {
            TimeZone.setDefault(tz)
        }
    }

    @Test
    fun formatClockTimeZeroIsZeroZero() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("00:00", formatClockTime(0L))
        } finally {
            TimeZone.setDefault(tz)
        }
    }
}

class FormatCountdownTest {
    @Test
    fun underOneHourFormatsMmSs() {
        assertEquals("05:37", formatCountdown(5 * 60 + 37))
    }

    @Test
    fun exactlyOneHourFormatsHhMmSs() {
        assertEquals("01:00:00", formatCountdown(3600))
    }

    @Test
    fun overOneHourFormatsHhMmSs() {
        assertEquals("01:02:03", formatCountdown(3600 + 2 * 60 + 3))
    }

    @Test
    fun zeroSecondsFormatsAsMmSs() {
        assertEquals("00:00", formatCountdown(0))
    }
}
