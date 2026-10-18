package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.ui.components.BASE_GLINT_CYCLE_MS
import ir.alifaniani.hawklyx.ui.components.BASE_MERIDIAN_CYCLE_MS
import ir.alifaniani.hawklyx.ui.components.HawklyxMeridianAnimationSpeed
import ir.alifaniani.hawklyx.ui.components.MeridianAnimationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HawklyxMeridianAnimationTest {

    @Test
    fun defaultSpeedMultiplier_isConfiguredCalmSpeed() {
        assertEquals(0.7f, HawklyxMeridianAnimationSpeed, 0.001f)
    }

    @Test
    fun calculateDurationMs_atOriginalSpeed_matchesBaseDurations() {
        val meridianMs = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, 1.0f)
        val glintMs = MeridianAnimationConfig.calculateDurationMs(BASE_GLINT_CYCLE_MS, 1.0f)

        assertEquals(3600, meridianMs)
        assertEquals(2400, glintMs)
    }

    @Test
    fun calculateDurationMs_atDefaultHalfSpeed_doublesDurationsToSlowDown() {
        val meridianMs = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, 0.5f)
        val glintMs = MeridianAnimationConfig.calculateDurationMs(BASE_GLINT_CYCLE_MS, 0.5f)

        // At half speed (0.5x), durations are doubled: 7.2s and 4.8s
        assertEquals(7200, meridianMs)
        assertEquals(4800, glintMs)
    }

    @Test
    fun calculateDurationMs_atQuarterSlower_scalesCorrectly() {
        val meridianMs = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, 0.75f)
        val glintMs = MeridianAnimationConfig.calculateDurationMs(BASE_GLINT_CYCLE_MS, 0.75f)

        // 3600 / 0.75 = 4800ms; 2400 / 0.75 = 3200ms
        assertEquals(4800, meridianMs)
        assertEquals(3200, glintMs)
    }

    @Test
    fun calculateDurationMs_atFasterSpeed_reducesDurations() {
        val meridianMs = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, 1.5f)
        val glintMs = MeridianAnimationConfig.calculateDurationMs(BASE_GLINT_CYCLE_MS, 1.5f)

        // 3600 / 1.5 = 2400ms; 2400 / 1.5 = 1600ms
        assertEquals(2400, meridianMs)
        assertEquals(1600, glintMs)
    }

    @Test
    fun calculateDurationMs_guardsAgainstInvalidValues() {
        // Zero, negative, NaN, and Infinite values must never crash or produce negative/zero durations
        val zeroSpeed = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, 0.0f)
        val negativeSpeed = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, -0.5f)
        val nanSpeed = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, Float.NaN)
        val infSpeed = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, Float.POSITIVE_INFINITY)

        assertTrue("Zero speed must produce positive duration", zeroSpeed > 0)
        assertTrue("Negative speed must produce positive duration", negativeSpeed > 0)
        assertTrue("NaN speed must produce positive duration", nanSpeed > 0)
        assertTrue("Infinite speed must produce positive duration", infSpeed > 0)

        // Fallbacks should match the default speed duration
        val defaultDuration = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, HawklyxMeridianAnimationSpeed)
        assertEquals(defaultDuration, zeroSpeed)
        assertEquals(defaultDuration, negativeSpeed)
        assertEquals(defaultDuration, nanSpeed)
    }

    @Test
    fun animationDurations_maintainHarmonicRatio() {
        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f).forEach { speed ->
            val meridianMs = MeridianAnimationConfig.calculateDurationMs(BASE_MERIDIAN_CYCLE_MS, speed).toDouble()
            val glintMs = MeridianAnimationConfig.calculateDurationMs(BASE_GLINT_CYCLE_MS, speed).toDouble()

            val ratio = meridianMs / glintMs
            assertEquals("3:2 harmonic ratio must be preserved across speeds", 1.5, ratio, 0.05)
        }
    }
}
