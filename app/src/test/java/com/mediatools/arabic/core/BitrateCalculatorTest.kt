package com.mediatools.arabic.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitrateCalculatorTest {

    @Test
    fun fifteenMbForTenMinutes_isAbout196Kbps() {
        val kbps = BitrateCalculator.suggestedKbps(15 * BitrateCalculator.BYTES_PER_MB, 600_000)
        assertEquals(196, kbps)
    }

    @Test
    fun estimateStaysUnderTargetAtSuggestedBitrate() {
        val target = 15 * BitrateCalculator.BYTES_PER_MB
        val kbps = BitrateCalculator.suggestedKbps(target, 600_000)
        assertTrue(BitrateCalculator.estimateBytes(kbps, 600_000) <= target + 300_000)
    }

    @Test
    fun veryLongAudioForSmallTarget_isNotFeasible() {
        val kbps = BitrateCalculator.suggestedKbps(5 * BitrateCalculator.BYTES_PER_MB, 3 * 3600 * 1000L)
        assertFalse(BitrateCalculator.isFeasible(kbps))
    }

    @Test
    fun nextAttemptLowersBitrateWithSafetyMargin() {
        val next = BitrateCalculator.nextAttemptKbps(200, 15_000_000, 16_000_000)
        assertEquals(181, next)
    }

    @Test
    fun mbAndMibDiffer() {
        assertTrue(BitrateCalculator.BYTES_PER_MIB > BitrateCalculator.BYTES_PER_MB)
    }
}
