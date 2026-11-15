package com.naru.app.comma

import org.junit.Assert.assertEquals
import org.junit.Test

class CommaTimeStepperTest {
    @Test fun stepsByFiveMinutes() {
        assertEquals(20, stepCommaMinutes(15, 1))
        assertEquals(10, stepCommaMinutes(15, -1))
    }
    @Test fun clampsToSupportedRange() {
        assertEquals(1, stepCommaMinutes(1, -1))
        assertEquals(1, stepCommaMinutes(3, -1))
        assertEquals(180, stepCommaMinutes(178, 1))
        assertEquals(180, stepCommaMinutes(180, 1))
    }
    @Test fun preservesArbitraryMinuteInput() {
        assertEquals(22, stepCommaMinutes(17, 1))
        assertEquals(12, stepCommaMinutes(17, -1))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidMinutes() { stepCommaMinutes(0, 1) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDirection() { stepCommaMinutes(15, 0) }
}
