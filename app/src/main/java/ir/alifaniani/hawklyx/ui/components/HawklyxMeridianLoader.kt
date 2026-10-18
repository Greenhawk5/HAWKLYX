package ir.alifaniani.hawklyx.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Global default animation speed multiplier for the Meridian globe loader.
 *
 * Speed semantics:
 * - 1.0f = original reference speed (3.6s meridian cycle, 2.4s rim glint cycle)
 * - 0.75f = 25% slower (4.8s meridian cycle, 3.2s rim glint cycle)
 * - 0.5f = half original speed (calm, premium financial instrument pace: 7.2s meridian, 4.8s glint)
 * - 1.5f = 50% faster (2.4s meridian cycle, 1.6s rim glint cycle)
 *
 * Adjust this single value manually to tune the animation speed throughout the application.
 */
const val HawklyxMeridianAnimationSpeed: Float = 0.7f

/** Base reference cycle duration for meridian deformation at 1.0f speed (milliseconds). */
const val BASE_MERIDIAN_CYCLE_MS: Float = 3600f

/** Base reference cycle duration for perimeter rim highlight rotation at 1.0f speed (milliseconds). */
const val BASE_GLINT_CYCLE_MS: Float = 2400f

object MeridianAnimationConfig {
    /**
     * Calculates the effective animation duration in milliseconds.
     * Higher speed multiplier -> shorter duration (faster animation).
     * Lower speed multiplier -> longer duration (slower animation).
     *
     * Validates and guards against zero, negative, or non-finite values by falling back to safe limits.
     */
    fun calculateDurationMs(baseDurationMs: Float, speedMultiplier: Float): Int {
        val safeSpeed = if (speedMultiplier.isFinite() && speedMultiplier > 0.01f) {
            speedMultiplier.coerceIn(0.05f, 10.0f)
        } else {
            HawklyxMeridianAnimationSpeed
        }
        return (baseDurationMs / safeSpeed).roundToInt()
    }
}

/**
 * Native Jetpack Compose implementation of the Dominik Kissi Meridian Loader
 * (https://circleloaders.dominikakissi.com).
 *
 * Visual characteristics:
 * - Ultra-fine spherical wireframe with restrained emerald / amber accents.
 * - Horizontal latitude chords at 60°, 30°, 0°, -30°, -60°.
 * - Continuously revolving 3D meridian ellipses where rx = R * |cos(phase + offset)|.
 * - Orbiting perimeter rim highlight (glint).
 * - Lifecycle-aware, ultra-lightweight, and respects system accessibility / reduced motion.
 */
@Composable
fun HawklyxMeridianLoader(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    strokeWidth: Dp = 1.2.dp,
    accentColor: Color = HawklyxEmerald,
    wireframeColor: Color = HawklyxEmerald.copy(alpha = 0.28f),
    rimBaseColor: Color = HawklyxEmerald.copy(alpha = 0.38f),
    forceStatic: Boolean = false,
    speedMultiplier: Float = HawklyxMeridianAnimationSpeed
) {
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale <= 0.05f
        } catch (_: Exception) {
            false
        }
    }

    if (isReducedMotion || forceStatic) {
        StaticMeridianInstrument(
            modifier = modifier,
            size = size,
            strokeWidth = strokeWidth,
            wireframeColor = wireframeColor,
            rimBaseColor = rimBaseColor
        )
    } else {
        AnimatedMeridianInstrument(
            modifier = modifier,
            size = size,
            strokeWidth = strokeWidth,
            accentColor = accentColor,
            wireframeColor = wireframeColor,
            rimBaseColor = rimBaseColor,
            speedMultiplier = speedMultiplier
        )
    }
}

@Composable
private fun AnimatedMeridianInstrument(
    modifier: Modifier,
    size: Dp,
    strokeWidth: Dp,
    accentColor: Color,
    wireframeColor: Color,
    rimBaseColor: Color,
    speedMultiplier: Float
) {
    val meridianDurationMs = MeridianAnimationConfig.calculateDurationMs(
        BASE_MERIDIAN_CYCLE_MS,
        speedMultiplier
    )
    val glintDurationMs = MeridianAnimationConfig.calculateDurationMs(
        BASE_GLINT_CYCLE_MS,
        speedMultiplier
    )

    val infiniteTransition = rememberInfiniteTransition(label = "meridian_instrument")

    // Full 360-degree rotation phase for meridian deforming (configurable cycle)
    val phaseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = meridianDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meridian_phase"
    )

    // Moving rim glint along outer boundary (configurable cycle)
    val glintAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = glintDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glint_angle"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val canvasW = this.size.width
            val canvasH = this.size.height
            val radius = (canvasW.coerceAtMost(canvasH) - strokePx * 2f) / 2f
            val cx = canvasW / 2f
            val cy = canvasH / 2f

            if (radius <= 0f) return@Canvas

            // 1. Static Outer Perimeter Ring
            drawCircle(
                color = rimBaseColor,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokePx)
            )

            // 2. Horizontal Latitude Chords (Equator + 30° / 60° north & south)
            // Normalized y-offsets: -0.866R, -0.5R, 0, 0.5R, 0.866R
            val latitudeNorms = floatArrayOf(-0.866f, -0.5f, 0f, 0.5f, 0.866f)
            for (normY in latitudeNorms) {
                val dy = normY * radius
                val chordHalfWidth = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
                if (chordHalfWidth > 0.5f) {
                    drawLine(
                        color = wireframeColor,
                        start = Offset(cx - chordHalfWidth, cy + dy),
                        end = Offset(cx + chordHalfWidth, cy + dy),
                        strokeWidth = strokePx * 0.75f
                    )
                }
            }

            // 3. Rotating 3D Meridian Ellipses (6 meridians spaced by PI / 6)
            val radPhase = (phaseAngle * (PI / 180.0)).toFloat()
            val numMeridians = 6
            for (i in 0 until numMeridians) {
                val offset = (i.toFloat() * (PI / numMeridians.toFloat())).toFloat()
                val rx = radius * abs(cos(radPhase + offset))
                if (rx > 0.75f) {
                    drawOval(
                        color = wireframeColor,
                        topLeft = Offset(cx - rx, cy - radius),
                        size = Size(rx * 2f, radius * 2f),
                        style = Stroke(width = strokePx * 0.85f)
                    )
                } else {
                    drawLine(
                        color = wireframeColor,
                        start = Offset(cx, cy - radius),
                        end = Offset(cx, cy + radius),
                        strokeWidth = strokePx * 0.85f
                    )
                }
            }

            // 4. Moving Rim Glint (Accent Highlight Orbiting Perimeter)
            // Soft halo
            drawArc(
                color = accentColor.copy(alpha = 0.35f),
                startAngle = glintAngle - 10f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx * 2.2f, cap = StrokeCap.Round)
            )
            // Bright focal glint
            drawArc(
                color = accentColor,
                startAngle = glintAngle,
                sweepAngle = 35f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx * 1.8f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun StaticMeridianInstrument(
    modifier: Modifier,
    size: Dp,
    strokeWidth: Dp,
    wireframeColor: Color,
    rimBaseColor: Color
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val radius = (this.size.width.coerceAtMost(this.size.height) - strokePx * 2f) / 2f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            if (radius <= 0f) return@Canvas

            // Perimeter
            drawCircle(
                color = rimBaseColor,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokePx)
            )

            // Latitude chords
            val latitudeNorms = floatArrayOf(-0.866f, -0.5f, 0f, 0.5f, 0.866f)
            for (normY in latitudeNorms) {
                val dy = normY * radius
                val chordHalfWidth = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
                if (chordHalfWidth > 0.5f) {
                    drawLine(
                        color = wireframeColor,
                        start = Offset(cx - chordHalfWidth, cy + dy),
                        end = Offset(cx + chordHalfWidth, cy + dy),
                        strokeWidth = strokePx * 0.75f
                    )
                }
            }

            // Static meridian arches
            val staticRxs = floatArrayOf(radius * 0.866f, radius * 0.5f, 0f)
            for (rx in staticRxs) {
                if (rx > 0.5f) {
                    drawOval(
                        color = wireframeColor,
                        topLeft = Offset(cx - rx, cy - radius),
                        size = Size(rx * 2f, radius * 2f),
                        style = Stroke(width = strokePx * 0.85f)
                    )
                } else {
                    drawLine(
                        color = wireframeColor,
                        start = Offset(cx, cy - radius),
                        end = Offset(cx, cy + radius),
                        strokeWidth = strokePx * 0.85f
                    )
                }
            }
        }
    }
}
