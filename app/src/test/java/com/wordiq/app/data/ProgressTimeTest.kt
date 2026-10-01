package com.wordiq.app.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTimeTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun weekStartsAtLocalMondayMidnight() {
        val sunday = millis(berlin, 2026, Calendar.AUGUST, 16, 18, 45)
        val expectedMonday = millis(berlin, 2026, Calendar.AUGUST, 10, 0, 0)

        assertEquals(expectedMonday, startOfLocalWeekMillis(sunday, berlin))
    }

    @Test
    fun practiceDaysUseLocalDatesInsteadOfUtcBuckets() {
        val beforeUtcMidnight = millis(utc, 2026, Calendar.AUGUST, 16, 23, 30)
        val afterUtcMidnight = millis(utc, 2026, Calendar.AUGUST, 17, 0, 30)
        val nextLocalDay = millis(utc, 2026, Calendar.AUGUST, 17, 22, 30)

        assertEquals(1, localPracticeDayCount(listOf(beforeUtcMidnight, afterUtcMidnight), berlin))
        assertEquals(2, localPracticeDayCount(listOf(beforeUtcMidnight, afterUtcMidnight, nextLocalDay), berlin))
    }

    private fun millis(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month, day, hour, minute)
    }.timeInMillis
}
