package com.mediatools.arabic.core

import kotlin.math.floor

/**
 * حسابات معدل البت للحجم المستهدف.
 * MB هنا عشري (1,000,000 بايت). MiB = 1,048,576 بايت.
 */
object BitrateCalculator {
    const val BYTES_PER_MB = 1_000_000L
    const val BYTES_PER_MIB = 1_048_576L
    const val MIN_KBPS = 32
    const val MAX_KBPS = 320
    const val MAX_ATTEMPTS = 4

    /** هامش للحاويات والبيانات الإضافية. */
    private const val OVERHEAD = 0.02

    fun suggestedKbps(targetBytes: Long, durationMs: Long): Int {
        require(targetBytes > 0) { "targetBytes must be positive" }
        require(durationMs > 0) { "durationMs must be positive" }
        val usableBits = targetBytes * (1.0 - OVERHEAD) * 8.0
        val seconds = durationMs / 1000.0
        return floor(usableBits / seconds / 1000.0).toInt()
    }

    fun estimateBytes(kbps: Int, durationMs: Long): Long {
        val bytes = kbps * 1000.0 / 8.0 * (durationMs / 1000.0)
        return (bytes * (1.0 + OVERHEAD)).toLong()
    }

    fun isFeasible(kbps: Int): Boolean = kbps >= MIN_KBPS

    /** معدل بت جديد بعد محاولة تجاوزت الهدف، مع هامش أمان 3%. */
    fun nextAttemptKbps(currentKbps: Int, targetBytes: Long, actualBytes: Long): Int {
        require(actualBytes > 0)
        return floor(currentKbps * (targetBytes.toDouble() / actualBytes) * 0.97).toInt()
    }
}
