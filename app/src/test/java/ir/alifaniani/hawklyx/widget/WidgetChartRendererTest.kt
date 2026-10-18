package ir.alifaniani.hawklyx.widget

import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying data integrity and bounds calculation for the WidgetChartRenderer.
 * Ensures the chart engine strictly rejects synthetic or insufficient data.
 */
class WidgetChartRendererTest {

    private fun createPoint(timeMs: Long, price: Double): HistoricalPointModel {
        return HistoricalPointModel(
            timestamp = "2026-10-18T12:00:00Z",
            timestampMs = timeMs,
            price = price,
            formatted = "$%.2f".format(price),
            asOf = "2026-10-18T12:00:00Z",
            fetchedAt = "2026-10-18T12:05:00Z"
        )
    }

    @Test
    fun emptyList_isInvalid_andReturnsNull() {
        assertFalse(WidgetChartRenderer.isValidDataset(emptyList()))
        assertNull(WidgetChartRenderer.renderTrendChart(emptyList()))
    }

    @Test
    fun singlePoint_isInvalid_neverSynthesizesCurve() {
        val points = listOf(createPoint(1000L, 85.50))
        assertFalse("Single point must be rejected to prevent fabricated curves", WidgetChartRenderer.isValidDataset(points))
        assertNull("Single point must return null bitmap so layout shows accumulation state", WidgetChartRenderer.renderTrendChart(points))
    }

    @Test
    fun invalidPrices_filteredOutSafely() {
        val points = listOf(
            createPoint(1000L, -10.0),
            createPoint(2000L, 0.0),
            createPoint(3000L, Double.NaN),
            createPoint(4000L, 85.50)
        )
        // Only 1 point is valid, so overall dataset must be invalid
        assertFalse(WidgetChartRenderer.isValidDataset(points))
        assertEquals(1, WidgetChartRenderer.getValidPoints(points).size)
        assertNull(WidgetChartRenderer.renderTrendChart(points))
    }

    @Test
    fun twoValidPoints_isValidDataset() {
        val points = listOf(
            createPoint(1000L, 84.50),
            createPoint(2000L, 86.20)
        )
        assertTrue(WidgetChartRenderer.isValidDataset(points))
        val bounds = WidgetChartRenderer.calculatePriceBounds(WidgetChartRenderer.getValidPoints(points))
        assertNotNull(bounds)
        assertTrue(bounds!!.first < 84.50)
        assertTrue(bounds.second > 86.20)
    }

    @Test
    fun flatPrices_handledWithoutDivisionByZero() {
        val points = listOf(
            createPoint(1000L, 85.00),
            createPoint(2000L, 85.00),
            createPoint(3000L, 85.00)
        )
        assertTrue(WidgetChartRenderer.isValidDataset(points))
        val bounds = WidgetChartRenderer.calculatePriceBounds(WidgetChartRenderer.getValidPoints(points))
        assertNotNull(bounds)
        // Padded min must be strictly less than padded max to prevent zero division
        assertTrue("Min bound must be less than max bound", bounds!!.first < bounds.second)
    }

    @Test
    fun chronologicalSorting_ensuresCorrectTimeline() {
        val points = listOf(
            createPoint(3000L, 86.00),
            createPoint(1000L, 84.00),
            createPoint(2000L, 85.00)
        )
        val sorted = WidgetChartRenderer.getValidPoints(points)
        assertEquals(1000L, sorted[0].timestampMs)
        assertEquals(2000L, sorted[1].timestampMs)
        assertEquals(3000L, sorted[2].timestampMs)
    }
}
