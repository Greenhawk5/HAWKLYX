package ir.alifaniani.hawklyx.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.HistoricalObservationPoint
import ir.alifaniani.hawklyx.ui.theme.HawklyxBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmeraldGlow
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurface
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurfaceVariant
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Premium Native Compose Market History Area & Line Chart.
 * Adheres strictly to the genuine data integrity specification:
 * NEVER fabricates synthetic or imaginary price movements.
 */
@Composable
fun MarketHistoryChart(
    history: List<HistoricalObservationPoint>,
    currentObservation: BrentPriceObservation?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GENUINE MARKET HISTORY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = HawklyxTextSecondary
                    )
                )
                Text(
                    text = if (history.size >= 2) "${history.size} verified observations" else "Verified trend capture",
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
                )
            }

            val badgeText = if (history.size >= 2) "LIVE TREND" else "ACCUMULATING"
            val badgeColor = if (history.size >= 2) HawklyxEmerald else HawklyxTextSecondary

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HawklyxSurfaceVariant)
                    .border(1.dp, HawklyxBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.size >= 2) {
            ActiveHistoryCanvasChart(points = history)
        } else {
            AccumulatingHistoryState(currentObservation = currentObservation)
        }
    }
}

@Composable
private fun ActiveHistoryCanvasChart(points: List<HistoricalObservationPoint>) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val prices = points.map { it.price }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 0.0
    val priceRange = if (maxPrice > minPrice) maxPrice - minPrice else 1.0

    val selectedPoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()

    // Inspection pill
    if (selectedPoint != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedPoint.formatted,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = HawklyxEmerald
                )
            )
            Text(
                text = formatHistoryTimestamp(selectedPoint.timestampMs),
                style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextSecondary)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        selectedIndex = (ratio * (points.size - 1)).toInt()
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures { change, _ ->
                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                    selectedIndex = (ratio * (points.size - 1)).toInt()
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val padTop = 12f
            val padBottom = 20f
            val availableHeight = height - padTop - padBottom

            // 1. Draw subtle horizontal gridlines
            val gridLines = 3
            for (i in 0..gridLines) {
                val y = padTop + (availableHeight * (i.toFloat() / gridLines))
                drawLine(
                    color = Color(0xFF242B26),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // 2. Map coordinates
            val coords = points.mapIndexed { index, pt ->
                val x = (index.toFloat() / (points.size - 1)) * width
                val normalizedY = ((pt.price - minPrice) / priceRange).toFloat()
                val y = padTop + (availableHeight * (1f - normalizedY))
                Offset(x, y)
            }

            // 3. Draw gradient area
            val fillPath = Path().apply {
                moveTo(coords.first().x, height - padBottom)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, height - padBottom)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HawklyxEmerald.copy(alpha = 0.22f),
                        HawklyxEmerald.copy(alpha = 0.02f)
                    ),
                    startY = padTop,
                    endY = height - padBottom
                )
            )

            // 4. Draw continuous line
            val linePath = Path().apply {
                coords.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }

            drawPath(
                path = linePath,
                color = HawklyxEmerald,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 5. Draw observation nodes
            coords.forEachIndexed { index, offset ->
                val isSelected = (index == selectedIndex)
                val radius = if (isSelected) 5.dp.toPx() else 2.5.dp.toPx()
                val alpha = if (isSelected) 1f else 0.7f

                if (isSelected) {
                    drawCircle(
                        color = HawklyxEmeraldGlow,
                        radius = radius * 2.2f,
                        center = offset
                    )
                }
                drawCircle(
                    color = HawklyxEmerald.copy(alpha = alpha),
                    radius = radius,
                    center = offset
                )
            }
        }
    }

    // Min / Max range footer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Min: $%.2f".format(minPrice),
            style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
        )
        Text(
            text = "Max: $%.2f".format(maxPrice),
            style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
        )
    }
}

@Composable
private fun AccumulatingHistoryState(currentObservation: BrentPriceObservation?) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_node")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(HawklyxEmerald)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Tracking Baseline Observation",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = HawklyxTextPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "HAWKLYX records verified market observations at each 15-minute edge cadence. A live historical area chart renders automatically as subsequent genuine data points are captured.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = HawklyxTextSecondary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Initial Quote",
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
                )
                Text(
                    text = currentObservation?.formatted ?: "$--.--",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = HawklyxEmerald
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Market As Of",
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
                )
                Text(
                    text = currentObservation?.asOf?.take(19)?.replace("T", " ") ?: "Awaiting sync",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = HawklyxTextPrimary
                    )
                )
            }
        }
    }
}

private fun formatHistoryTimestamp(millis: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(millis)
        val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        ""
    }
}
