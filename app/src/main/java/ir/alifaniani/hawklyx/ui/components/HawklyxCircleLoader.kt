package ir.alifaniani.hawklyx.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmeraldGlow

/**
 * Premium native Canvas circular loader inspired by minimalist financial indicators.
 * Draws dual orbiting emerald arcs with a subtle pulsating core node.
 */
@Composable
fun HawklyxCircleLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = HawklyxEmerald
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hawklyx_circle_loader")

    // Primary rotation (clockwise)
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerAngle"
    )

    // Dynamic sweep angle breathing
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = 260f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepAngle"
    )

    // Inner counter-rotation (counter-clockwise)
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerAngle"
    )

    // Center pulsating core
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val canvasW = this.size.width
            val canvasH = this.size.height

            // 1. Subtle background track
            drawCircle(
                color = color.copy(alpha = 0.12f),
                radius = (canvasW - strokePx) / 2f,
                style = Stroke(width = strokePx)
            )

            // 2. Primary outer orbiting arc
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to color.copy(alpha = 0.1f),
                    0.7f to color,
                    1.0f to color
                ),
                startAngle = outerAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(canvasW - strokePx, canvasH - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 3. Inner counter-rotating secondary ring
            val innerInset = strokePx * 2.2f
            drawArc(
                color = color.copy(alpha = 0.35f),
                startAngle = innerAngle,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = Size(canvasW - (innerInset * 2f), canvasH - (innerInset * 2f)),
                style = Stroke(width = strokePx * 0.7f, cap = StrokeCap.Round)
            )

            // 4. Center pulsating core node
            val centerRadius = (strokePx * 1.5f) * pulseScale
            drawCircle(
                color = HawklyxEmeraldGlow.copy(alpha = pulseAlpha * 0.5f),
                radius = centerRadius * 2.2f,
                center = center
            )
            drawCircle(
                color = color.copy(alpha = pulseAlpha),
                radius = centerRadius,
                center = center
            )
        }
    }
}

/**
 * Compact circular cooldown indicator for the manual refresh action.
 */
@Composable
fun HawklyxCooldownProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = HawklyxEmerald
) {
    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)

        // Background track
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx)
        )

        // Progress arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = (progress.coerceIn(0f, 1f)) * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}
