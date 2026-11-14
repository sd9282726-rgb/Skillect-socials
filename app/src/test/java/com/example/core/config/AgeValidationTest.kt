package com.example.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AgeValidationTest {
    @Test
    fun eligibleWhenExactly16() {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR) - 16
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val iso = "%04d-%02d-%02d".format(y, m, d)
        assertTrue(AppConfig.isEligibleAge16Plus(iso))
    }

    @Test
    fun rejectsUnder16() {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR) - 15
        val iso = "%04d-%02d-%02d".format(y, 6, 15)
        assertFalse(AppConfig.isEligibleAge16Plus(iso))
    }

    @Test
    fun minor16To17() {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR) - 16
        val iso = "%04d-01-01".format(y)
        assertTrue(AppConfig.isMinor16To17(iso))
    }

    @Test
    fun adultNotMinor() {
        assertFalse(AppConfig.isMinor16To17("1990-05-20"))
        assertTrue(AppConfig.isEligibleAge16Plus("1990-05-20"))
    }

    @Test
    fun invalidDateRejected() {
        assertEquals(null, AppConfig.calculateAgeYears("not-a-date"))
        assertFalse(AppConfig.isEligibleAge16Plus(""))
        assertFalse(AppConfig.isEligibleAge16Plus("2020-13-40"))
    }
}
