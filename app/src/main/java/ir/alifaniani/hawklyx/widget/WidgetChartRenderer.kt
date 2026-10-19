package ir.alifaniani.hawklyx.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.domain.PriceDirection

/**
 * Lightweight, static Android Canvas chart renderer for the HAWKLYX Market Intelligence widget.
 *
 * Guarantees:
 * - Pure Android Canvas/Paint rendering into an in-memory Bitmap compatible with RemoteViews.
 * - Strict Data Integrity: NEVER fabricates synthetic points, never invents curves for insufficient data.
 * - Returns null if fewer than 2 valid points exist (triggers honest accumulation/empty state in widget layout).
 * - Zero background threads, zero animation loops, zero third-party library dependencies.
 */
object WidgetChartRenderer {

    // Visual Palette aligned with HAWKLYX Design System
    const val COLOR_EMERALD = 0xFF62E59A.toInt()
    const val COLOR_EMERALD_FILL_TOP = 0x3362E59A.toInt()
    const val COLOR_RED = 0xFFFF6B6B.toInt()
    const val COLOR_RED_FILL_TOP = 0x33FF6B6B.toInt()
    const val COLOR_AMBER = 0xFFE5B862.toInt()
    const val COLOR_AMBER_FILL_TOP = 0x33E5B862.toInt()
    const val COLOR_GRID_LINE = 0xFF1C241F.toInt()
    const val COLOR_TRANSPARENT = 0x00000000

    /**
     * Resolves the authoritative line and terminal-dot color for a given market state.
     */
    fun resolveLineColor(
        isStale: Boolean,
        isMarketClosed: Boolean,
        sessionDirection: PriceDirection
    ): Int {
        return when {
            isMarketClosed || isStale -> COLOR_AMBER
            sessionDirection == PriceDirection.NEGATIVE -> COLOR_RED
            else -> COLOR_EMERALD
        }
    }

    /**
     * Filters for strictly valid points with genuine positive finite numbers.
     */
    fun getValidPoints(points: List<HistoricalPointModel>): List<HistoricalPointModel> {
        return points.filter { it.price > 0.0 && !it.price.isNaN() && !it.price.isInfinite() }
            .sortedBy { it.timestampMs }
    }

    /**
     * Authentic Data Guarantee: True only if at least 2 genuine observations exist.
     */
    fun isValidDataset(points: List<HistoricalPointModel>): Boolean {
        return getValidPoints(points).size >= 2
    }

    /**
     * Computes padded min and max price bounds with symmetric zero-division protection.
     */
    fun calculatePriceBounds(validPoints: List<HistoricalPointModel>): Pair<Double, Double>? {
        if (validPoints.size < 2) return null
        var minPrice = Double.MAX_VALUE
        var maxPrice = Double.MIN_VALUE
        for (pt in validPoints) {
            if (pt.price < minPrice) minPrice = pt.price
            if (pt.price > maxPrice) maxPrice = pt.price
        }
        val rawRange = maxPrice - minPrice
        val verticalBuffer = if (rawRange > 0.0001) rawRange * 0.12 else (if (minPrice > 0.0) minPrice * 0.01 else 1.0)
        return Pair(minPrice - verticalBuffer, maxPrice + verticalBuffer)
    }

    /**
     * Renders a static 7-day trend chart.
     *
     * @param points Chronological list of authentic historical price points.
     * @param widthPx Target bitmap width in pixels (defaults to 640px).
     * @param heightPx Target bitmap height in pixels (defaults to 260px).
     * @param isStale Whether the underlying data is cached/stale (renders in restrained amber).
     * @return A standalone Bitmap for RemoteViews, or null if points are insufficient (< 2).
     */
    fun renderTrendChart(
        points: List<HistoricalPointModel>,
        widthPx: Int = 640,
        heightPx: Int = 260,
        isStale: Boolean = false,
        isMarketClosed: Boolean = false,
        sessionDirection: PriceDirection = PriceDirection.NEUTRAL,
        sessionStartEpochMs: Long = 0L
    ): Bitmap? {
        val validPoints = getValidPoints(points)

        // Authentic Data Guarantee: Never synthesize curves for fewer than 2 genuine observations
        if (validPoints.size < 2 || widthPx <= 0 || heightPx <= 0) {
            return null
        }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paddingLeft = 16f
        val paddingRight = 20f
        val paddingTop = 22f
        val paddingBottom = 16f

        val plotWidth = (widthPx - paddingLeft - paddingRight).coerceAtLeast(1f)
        val plotHeight = (heightPx - paddingTop - paddingBottom).coerceAtLeast(1f)

        // Calculate min and max price
        var minPrice = Double.MAX_VALUE
        var maxPrice = Double.MIN_VALUE
        for (pt in validPoints) {
            if (pt.price < minPrice) minPrice = pt.price
            if (pt.price > maxPrice) maxPrice = pt.price
        }

        // Avoid division by zero on flat/identical prices with subtle symmetric buffer
        val rawRange = maxPrice - minPrice
        val verticalBuffer = if (rawRange > 0.0001) rawRange * 0.12 else (if (minPrice > 0.0) minPrice * 0.01 else 1.0)
        val paddedMin = minPrice - verticalBuffer
        val paddedMax = maxPrice + verticalBuffer
        val priceSpan = (paddedMax - paddedMin).coerceAtLeast(0.0001)

        // 1. Draw subtle horizontal baseline grid lines (at 25%, 50%, 75% height)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GRID_LINE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        for (i in 1..3) {
            val gridY = paddingTop + (plotHeight * (i / 4f))
            canvas.drawLine(paddingLeft, gridY, widthPx - paddingRight, gridY, gridPaint)
        }

        // 2. Map coordinates: X proportional to chronological index, Y inversely proportional to price
        val count = validPoints.size
        val xCoords = FloatArray(count)
        val yCoords = FloatArray(count)

        for (i in 0 until count) {
            xCoords[i] = paddingLeft + (i.toFloat() / (count - 1)) * plotWidth
            val normalizedPrice = ((validPoints[i].price - paddedMin) / priceSpan).coerceIn(0.0, 1.0)
            yCoords[i] = (paddingTop + (1.0 - normalizedPrice) * plotHeight).toFloat()
        }

        // 3. Resolve session bounds and color semantics
        val effectiveSessionStart = if (sessionStartEpochMs > 0L) {
            sessionStartEpochMs
        } else {
            val lastTs = validPoints.last().timestampMs
            ir.alifaniani.hawklyx.domain.BrentMarketCalendar.getSessionInfo(lastTs).sessionStartEpochMs
        }

        val sessionLineColor = resolveLineColor(isStale, isMarketClosed, sessionDirection)

        val sessionFillColor = when {
            isMarketClosed || isStale -> COLOR_AMBER_FILL_TOP
            sessionDirection == PriceDirection.NEGATIVE -> COLOR_RED_FILL_TOP
            else -> COLOR_EMERALD_FILL_TOP
        }

        // 4. Construct area fill and line paths with unified trend color
        val linePath = Path().apply {
            moveTo(xCoords[0], yCoords[0])
            for (i in 1 until count) {
                lineTo(xCoords[i], yCoords[i])
            }
        }

        val fillPath = Path(linePath).apply {
            lineTo(xCoords[count - 1], heightPx - paddingBottom)
            lineTo(xCoords[0], heightPx - paddingBottom)
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, paddingTop,
                0f, heightPx - paddingBottom,
                sessionFillColor, COLOR_TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = sessionLineColor
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(linePath, strokePaint)

        // 5. Draw terminal dot & halo at latest observation
        val lastX = xCoords[count - 1]
        val lastY = yCoords[count - 1]

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = sessionLineColor
            alpha = 70
        }
        canvas.drawCircle(lastX, lastY, 7.5f, haloPaint)

        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = sessionLineColor
        }
        canvas.drawCircle(lastX, lastY, 3.5f, pointPaint)

        // 8. Draw high/low reference watermark text at right edge
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF68756D.toInt()
            textSize = 18f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("H: $%.2f".format(maxPrice), widthPx - paddingRight, paddingTop - 4f, labelPaint)
        canvas.drawText("L: $%.2f".format(minPrice), widthPx - paddingRight, heightPx - paddingBottom + 12f, labelPaint)

        return bitmap
    }
}
