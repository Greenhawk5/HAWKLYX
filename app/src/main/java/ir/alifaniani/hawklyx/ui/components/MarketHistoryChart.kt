package ir.alifaniani.hawklyx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalObservationPoint
import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
import ir.alifaniani.hawklyx.data.model.HistorySummaryModel
import ir.alifaniani.hawklyx.data.model.HistoryUiState
import ir.alifaniani.hawklyx.ui.theme.HawklyxAmber
import ir.alifaniani.hawklyx.ui.theme.HawklyxBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonClosedBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonClosedContainer
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonClosedContent
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonEmeraldBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonEmeraldContainer
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmeraldGlow
import ir.alifaniani.hawklyx.ui.theme.HawklyxRed
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurface
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurfaceVariant
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary
import ir.alifaniani.hawklyx.domain.BrentMarketCalendar
import ir.alifaniani.hawklyx.domain.HawklyxPriceColorResolver
import ir.alifaniani.hawklyx.domain.PriceDirection
import ir.alifaniani.hawklyx.util.HawklyxTimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Premium Native Compose Market History Area & Line Chart.
 * Adheres strictly to the genuine data integrity specification:
 * NEVER fabricates synthetic or imaginary price movements.
 */
/**
 * Centralized color resolver for the initial/current price in Market History.
 * Follows identical semantic rules to the rest of HAWKLYX:
 * - Market open & fresh: HawklyxEmerald (when positive), HawklyxRed (when negative)
 * - Market closed / stale / offline: HawklyxAmber
 * - Quota exhausted: HawklyxTextSecondary
 * - No data: HawklyxTextMuted
 */
object MarketHistoryPriceColorResolver {
    fun resolve(
        observation: BrentPriceObservation?,
        status: DataFreshnessStatus,
        isMarketClosed: Boolean,
        sessionDirection: PriceDirection = PriceDirection.NEUTRAL,
        isBaselineAvailable: Boolean = true
    ): Color = HawklyxPriceColorResolver.resolveComposeColor(
        observation = observation,
        status = status,
        isMarketClosed = isMarketClosed,
        sessionDirection = sessionDirection,
        isBaselineAvailable = isBaselineAvailable
    )
}

@Composable
fun MarketHistoryChart(
    historyUiState: HistoryUiState,
    currentObservation: BrentPriceObservation?,
    status: DataFreshnessStatus = DataFreshnessStatus.FRESH,
    isMarketClosed: Boolean = false,
    sessionDirection: PriceDirection = PriceDirection.NEUTRAL,
    isBaselineAvailable: Boolean = true,
    onSelectRange: (HistoryRange) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val historyData = historyUiState.data
    val points = historyData?.points ?: emptyList()
    val summary = historyData?.summary
    val selectedRange = historyUiState.selectedRange

    val currentPriceColor = MarketHistoryPriceColorResolver.resolve(
        observation = currentObservation,
        status = status,
        isMarketClosed = isMarketClosed,
        sessionDirection = sessionDirection,
        isBaselineAvailable = isBaselineAvailable
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        // 1. Header: Section Title + Range Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MARKET HISTORY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = HawklyxTextSecondary
                    )
                )
                Text(
                    text = if (points.size >= 2) {
                        "${points.size} verified ${if (historyData?.resolution == "daily") "daily" else "hourly"} points"
                    } else {
                        "Verified historical edge capture"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
                )
            }

            // Status Badge
            HistoryStatusBadge(
                isStale = historyUiState.isStale,
                isMarketClosed = isMarketClosed,
                isPartial = historyData?.isPartial == true,
                hasPoints = points.size >= 2,
                resolvedColor = currentPriceColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Compact Segmented Range Selector (1D, 1W, 1M, 6M, 1Y)
        HistoryRangeSelector(
            selectedRange = selectedRange,
            onSelectRange = onSelectRange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Chart Body: Loading, Active Chart, or Accumulating State
        when {
            historyUiState.isLoading && historyData == null -> {
                HistoryLoadingState()
            }
            points.size >= 2 -> {
                ActiveHistoryCanvasChart(
                    points = points,
                    summary = summary,
                    range = selectedRange,
                    resolution = historyData?.resolution ?: "hourly",
                    currentObservation = currentObservation,
                    status = status,
                    isMarketClosed = isMarketClosed,
                    sessionDirection = sessionDirection,
                    currentPriceColor = currentPriceColor
                )
            }
            else -> {
                AccumulatingHistoryState(
                    currentObservation = currentObservation,
                    collectionStartDate = historyData?.collectionStartDate,
                    priceColor = currentPriceColor
                )
            }
        }
    }
}

/**
 * Backwards compatibility overload for legacy callers.
 */
@Composable
fun MarketHistoryChart(
    history: List<HistoricalObservationPoint>,
    currentObservation: BrentPriceObservation?,
    modifier: Modifier = Modifier
) {
    val syntheticPoints = history.map {
        HistoricalPointModel(
            timestamp = Instant.ofEpochMilli(it.timestampMs).toString(),
            timestampMs = it.timestampMs,
            price = it.price,
            formatted = it.formatted,
            asOf = it.asOfIso,
            fetchedAt = it.asOfIso
        )
    }
    val fallbackData = if (syntheticPoints.isNotEmpty()) {
        HistoryResponseModel(
            status = "success",
            count = syntheticPoints.size,
            points = syntheticPoints
        )
    } else null

    MarketHistoryChart(
        historyUiState = HistoryUiState(
            selectedRange = HistoryRange.ONE_MONTH,
            data = fallbackData
        ),
        currentObservation = currentObservation,
        modifier = modifier
    )
}

@Composable
private fun HistoryRangeSelector(
    selectedRange: HistoryRange,
    onSelectRange: (HistoryRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryRange.entries.forEach { range ->
            val isSelected = (range == selectedRange)
            val containerColor = if (isSelected) HawklyxButtonEmeraldContainer else Color.Transparent
            val borderColor = if (isSelected) HawklyxButtonEmeraldBorder else Color.Transparent
            val textColor = if (isSelected) HawklyxEmerald else HawklyxTextMuted

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(containerColor)
                    .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                    .clickable { onSelectRange(range) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun HistoryStatusBadge(
    isStale: Boolean,
    isMarketClosed: Boolean,
    isPartial: Boolean,
    hasPoints: Boolean,
    resolvedColor: Color = HawklyxEmerald
) {
    val (badgeText, badgeBg, badgeBorder, badgeColor) = when {
        isStale -> Quad(
            "CACHED",
            HawklyxButtonClosedContainer,
            HawklyxButtonClosedBorder,
            HawklyxAmber
        )
        isMarketClosed -> Quad(
            "MARKET CLOSED",
            HawklyxButtonClosedContainer,
            HawklyxButtonClosedBorder,
            HawklyxButtonClosedContent
        )
        isPartial -> Quad(
            "PARTIAL RANGE",
            HawklyxSurfaceVariant,
            HawklyxBorder,
            HawklyxTextSecondary
        )
        hasPoints -> {
            val (bg, border) = when (resolvedColor) {
                HawklyxRed -> Pair(HawklyxRed.copy(alpha = 0.12f), HawklyxRed.copy(alpha = 0.30f))
                HawklyxAmber -> Pair(HawklyxButtonClosedContainer, HawklyxButtonClosedBorder)
                HawklyxEmerald -> Pair(HawklyxButtonEmeraldContainer, HawklyxButtonEmeraldBorder)
                else -> Pair(HawklyxSurfaceVariant, HawklyxBorder)
            }
            Quad(
                "LIVE TREND",
                bg,
                border,
                resolvedColor
            )
        }
        else -> Quad(
            "ACCUMULATING",
            HawklyxSurfaceVariant,
            HawklyxBorder,
            HawklyxTextMuted
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeBg)
            .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
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
                color = badgeColor,
                fontSize = 10.sp
            )
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun ActiveHistoryCanvasChart(
    points: List<HistoricalPointModel>,
    summary: HistorySummaryModel?,
    range: HistoryRange,
    resolution: String,
    currentObservation: BrentPriceObservation?,
    status: DataFreshnessStatus,
    isMarketClosed: Boolean,
    sessionDirection: PriceDirection,
    currentPriceColor: Color
) {
    var scrubbedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val prices = points.map { it.price }
    val minPrice = summary?.low ?: prices.minOrNull() ?: 0.0
    val maxPrice = summary?.high ?: prices.maxOrNull() ?: 0.0
    val priceRange = if (maxPrice > minPrice) maxPrice - minPrice else 1.0

    val nowMs = currentObservation?.asOf?.let {
        try { Instant.parse(it).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
    } ?: (points.lastOrNull()?.timestampMs ?: System.currentTimeMillis())
    val sessionInfo = BrentMarketCalendar.getSessionInfo(nowMs)
    val currentSessionStartMs = sessionInfo.sessionStartEpochMs

    val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
    val isScrubbing = scrubbedIndex != null
    val isCurrentSessionPoint = range == HistoryRange.ONE_DAY || (activePoint != null && activePoint.timestampMs >= currentSessionStartMs)

    // 1. Interactive Metrics Inspection Bar
    InspectionSummaryHeader(
        activePoint = activePoint,
        summary = summary,
        isScrubbing = isScrubbing,
        range = range,
        isDaily = resolution == "daily",
        currentPriceColor = currentPriceColor,
        isCurrentSessionPoint = isCurrentSessionPoint
    )

    Spacer(modifier = Modifier.height(10.dp))

    // 2. Canvas with Non-Blocking Horizontal Scrubbing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubbedIndex = (ratio * (points.size - 1)).toInt()
                    }
                )
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubbedIndex = (ratio * (points.size - 1)).toInt()
                    },
                    onHorizontalDrag = { change, _ ->
                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrubbedIndex = (ratio * (points.size - 1)).toInt()
                        change.consume()
                    },
                    onDragEnd = {
                        scrubbedIndex = null
                    },
                    onDragCancel = {
                        scrubbedIndex = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val padTop = 10f
            val padBottom = 22f
            val availableHeight = height - padTop - padBottom

            // A. Draw Subtle Horizontal Guidelines
            val gridLines = 3
            for (i in 0..gridLines) {
                val y = padTop + (availableHeight * (i.toFloat() / gridLines))
                drawLine(
                    color = Color(0xFF202621),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // B. Map Point Coordinates
            val coords = points.mapIndexed { index, pt ->
                val x = (index.toFloat() / (points.size - 1)) * width
                val normalizedY = ((pt.price - minPrice) / priceRange).toFloat()
                val y = padTop + (availableHeight * (1f - normalizedY))
                Offset(x, y)
            }

            // C. Authoritative Trend Color for All Visual Components
            val chartTrendColor = currentPriceColor

            // D. Draw Area Fill across all ranges (1D, 1W, 1M, 6M, 1Y)
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
                        chartTrendColor.copy(alpha = 0.20f),
                        chartTrendColor.copy(alpha = 0.01f)
                    ),
                    startY = padTop,
                    endY = height - padBottom
                )
            )

            // E. Draw Continuous Authentic Chart Line across all ranges (1D, 1W, 1M, 6M, 1Y)
            val linePath = Path().apply {
                coords.forEachIndexed { i, pt ->
                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }

            drawPath(
                path = linePath,
                color = chartTrendColor,
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // F. Data Points & Latest Observation Endpoint Marker
            if (points.size <= 32) {
                coords.forEachIndexed { index, offset ->
                    val isSelected = (index == scrubbedIndex)
                    val radius = if (isSelected) 4.5.dp.toPx() else 2.dp.toPx()
                    val alpha = if (isSelected) 1f else 0.5f

                    drawCircle(
                        color = chartTrendColor.copy(alpha = alpha),
                        radius = radius,
                        center = offset
                    )
                }
            } else {
                // Latest observation endpoint marker
                drawCircle(
                    color = chartTrendColor,
                    radius = 4.dp.toPx(),
                    center = coords.last()
                )
            }

            // G. Draw Interactive Crosshair & Target Node when Scrubbing
            scrubbedIndex?.let { idx ->
                val targetOffset = coords.getOrNull(idx)
                if (targetOffset != null) {
                    val targetColor = chartTrendColor
                    val glowColor = chartTrendColor.copy(alpha = 0.25f)

                    // Vertical Crosshair Guideline
                    drawLine(
                        color = targetColor.copy(alpha = 0.6f),
                        start = Offset(targetOffset.x, padTop),
                        end = Offset(targetOffset.x, height - padBottom),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Target Highlight Glow (Halo)
                    drawCircle(
                        color = glowColor,
                        radius = 12.dp.toPx(),
                        center = targetOffset
                    )
                    // Selected Point Ring
                    drawCircle(
                        color = targetColor,
                        radius = 5.dp.toPx(),
                        center = targetOffset
                    )
                    // Inner Cutout
                    drawCircle(
                        color = Color(0xFF101311),
                        radius = 2.5.dp.toPx(),
                        center = targetOffset
                    )
                }
            }
        }
    }

    // 3. Price & Date Bounds Footer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val firstDate = points.firstOrNull()?.timestampMs ?: 0L
        val lastDate = points.lastOrNull()?.timestampMs ?: 0L

        Text(
            text = formatBoundDate(firstDate, range),
            style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted, fontSize = 11.sp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "L: $%.2f".format(minPrice),
                style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted, fontSize = 11.sp)
            )
            Text(
                text = "H: $%.2f".format(maxPrice),
                style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted, fontSize = 11.sp)
            )
        }

        Text(
            text = formatBoundDate(lastDate, range),
            style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted, fontSize = 11.sp)
        )
    }
}

@Composable
private fun InspectionSummaryHeader(
    activePoint: HistoricalPointModel?,
    summary: HistorySummaryModel?,
    isScrubbing: Boolean,
    range: HistoryRange,
    isDaily: Boolean,
    currentPriceColor: Color,
    isCurrentSessionPoint: Boolean
) {
    if (activePoint == null) return

    val displayPriceColor = currentPriceColor

    val statusLabel = when {
        !isScrubbing -> "LATEST OBSERVATION"
        isCurrentSessionPoint -> "INSPECTING (CURRENT SESSION)"
        else -> "INSPECTING"
    }

    val statusLabelColor = when {
        !isScrubbing -> HawklyxTextMuted
        else -> currentPriceColor
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Price & Delta Column
            Column {
                Text(
                    text = activePoint.formatted,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = displayPriceColor,
                        fontSize = 24.sp
                    )
                )

                if (summary != null) {
                    val deltaAbs = summary.changeAbsolute
                    val deltaPct = summary.changePercent
                    val isPositive = deltaAbs >= 0.0
                    val deltaColor = if (isPositive) HawklyxEmerald else HawklyxRed
                    val deltaPrefix = if (isPositive) "+" else ""

                    Text(
                        text = "$deltaPrefix$%.2f ($deltaPrefix$%.2f%%) in range".format(deltaAbs, deltaPct),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = deltaColor,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Timestamp Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusLabelColor,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = formatInspectionTimestamp(activePoint.timestampMs, isDaily),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HawklyxTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Daily OHLC details (if active point is a daily aggregate)
        if (isDaily && activePoint.isDailyAggregate) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(HawklyxSurfaceVariant)
                    .border(1.dp, HawklyxBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "O: $%.2f".format(activePoint.open),
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextSecondary, fontSize = 10.sp)
                )
                Text(
                    text = "H: $%.2f".format(activePoint.high),
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextSecondary, fontSize = 10.sp)
                )
                Text(
                    text = "L: $%.2f".format(activePoint.low),
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextSecondary, fontSize = 10.sp)
                )
                Text(
                    text = "C: $%.2f".format(activePoint.close),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = currentPriceColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
                if (activePoint.sampleCount != null) {
                    Text(
                        text = "(${activePoint.sampleCount}h)",
                        style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted, fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryLoadingState() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(shimmerAlpha)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(HawklyxEmerald)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Retrieving verified historical points...",
                style = MaterialTheme.typography.bodyMedium.copy(color = HawklyxTextSecondary, fontSize = 12.sp)
            )
        }
    }
}

@Composable
private fun AccumulatingHistoryState(
    currentObservation: BrentPriceObservation?,
    collectionStartDate: String?,
    priceColor: Color = HawklyxEmerald
) {
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
                    .background(priceColor)
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
                        color = priceColor
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (collectionStartDate != null) "Collection Started" else "Market As Of",
                    style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
                )
                val timeString = if (collectionStartDate != null) {
                    HawklyxTimeUtils.formatIsoToDateTime(collectionStartDate)
                } else {
                    HawklyxTimeUtils.formatIsoToDateTime(currentObservation?.asOf)
                }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = HawklyxTextPrimary
                    )
                )
            }
        }
    }
}

private fun formatBoundDate(
    millis: Long,
    range: HistoryRange,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (millis <= 0L) return ""
    return try {
        val instant = Instant.ofEpochMilli(millis)
        val pattern = when (range) {
            HistoryRange.ONE_DAY -> "HH:mm"
            HistoryRange.ONE_WEEK -> "EEE HH:mm"
            HistoryRange.ONE_MONTH -> "MMM dd"
            HistoryRange.SIX_MONTHS, HistoryRange.ONE_YEAR -> "MMM yyyy"
        }
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(zone)
        formatter.format(instant)
    } catch (_: Exception) {
        ""
    }
}

private fun formatInspectionTimestamp(
    millis: Long,
    isDaily: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (millis <= 0L) return ""
    return try {
        val instant = Instant.ofEpochMilli(millis)
        val pattern = if (isDaily) "EEE, MMM dd, yyyy" else "MMM dd, HH:mm"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(zone)
        formatter.format(instant)
    } catch (_: Exception) {
        ""
    }
}
