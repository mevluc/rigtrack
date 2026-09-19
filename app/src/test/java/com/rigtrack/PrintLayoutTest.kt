package com.rigtrack

import com.rigtrack.printing.*
import org.junit.Assert.*
import org.junit.Test

class PrintLayoutTest {
    @Test fun exactPhysicalUnits() {
        assertEquals(72.0, PrintLayout.points(25.4), 1e-12)
        assertEquals(150.0, PrintLayout.points(150.0) * 25.4 / 72, 1e-12)
        assertEquals(300.0, PrintLayout.points(300.0) * 25.4 / 72, 1e-12)
    }
    @Test fun isoPaperAndOrientation() {
        for (paper in Paper.entries) for (landscape in listOf(false, true)) {
            val plan = PrintLayout.plan(PrintOptions(paper = paper, landscape = landscape, markerMm = 100.0))
            assertEquals(if (landscape) paper.heightMm else paper.widthMm, plan.widthMm, 0.0)
            assertEquals(if (landscape) paper.widthMm else paper.heightMm, plan.heightMm, 0.0)
        }
    }
    @Test fun pagePackingNeverScalesOrOverlaps() {
        val o = PrintOptions(paper = Paper.A3, markerMm = 100.0, firstId = 3, lastId = 21, multiple = true)
        val plan = PrintLayout.plan(o)
        assertEquals((3..21).toList(), plan.pages.flatten().map { it.id })
        plan.pages.forEach { page -> page.forEach { p ->
            assertEquals(100.0, p.widthMm, 0.0); assertEquals(100.0, p.heightMm, 0.0)
            assertTrue(p.xMm >= 10 && p.xMm + p.widthMm <= plan.widthMm - 10)
            assertTrue(p.yMm >= 15 && p.yMm + p.heightMm <= plan.heightMm - 35)
            page.filter { it !== p }.forEach { q -> assertTrue(p.xMm + p.widthMm <= q.xMm || q.xMm + q.widthMm <= p.xMm || p.yMm + p.heightMm <= q.yMm || q.yMm + q.heightMm <= p.yMm) }
        } }
    }
    @Test fun oversizePrintIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PrintLayout.plan(PrintOptions(markerMm = 200.0)) }
        assertThrows(IllegalArgumentException::class.java) { PrintLayout.plan(PrintOptions(paper = Paper.A3, markerMm = 300.0)) }
        // 300 mm is allowed only along the 420 mm A3 side; a square still exceeds the 297 mm side.
    }
    @Test fun charucoGeometryMatchesSettings() {
        val plan = PrintLayout.plan(PrintOptions(charuco = true, columns = 5, rows = 7, squareMm = 30.0, boardMarkerMm = 22.0))
        assertEquals(150.0, plan.pages.single().single().widthMm, 0.0)
        assertEquals(210.0, plan.pages.single().single().heightMm, 0.0)
        assertThrows(IllegalArgumentException::class.java) { PrintLayout.plan(PrintOptions(charuco = true, squareMm = 30.0, boardMarkerMm = 30.0)) }
    }
}
