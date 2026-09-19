package com.rigtrack.printing

import kotlin.math.floor

enum class Paper(val widthMm: Double, val heightMm: Double) { A4(210.0, 297.0), A3(297.0, 420.0) }
data class PrintOptions(
    val dictionary: Int = 20, val firstId: Int = 0, val lastId: Int = 0, val markerMm: Double = 150.0,
    val paper: Paper = Paper.A4, val landscape: Boolean = false, val multiple: Boolean = false,
    val labels: Boolean = true, val ruler: Boolean = true, val charuco: Boolean = false,
    val columns: Int = 5, val rows: Int = 7, val squareMm: Double = 30.0, val boardMarkerMm: Double = 22.0,
)
data class Placement(val id: Int, val xMm: Double, val yMm: Double, val widthMm: Double, val heightMm: Double)
data class PrintPlan(val widthMm: Double, val heightMm: Double, val pages: List<List<Placement>>)

object PrintLayout {
    fun points(mm: Double): Double = mm * 72.0 / 25.4
    fun plan(o: PrintOptions): PrintPlan {
        require(o.firstId >= 0 && o.lastId >= o.firstId && o.lastId - o.firstId < 200)
        require(o.markerMm.isFinite() && o.markerMm in 10.0..1000.0)
        require(o.columns in 3..20 && o.rows in 3..20 && o.squareMm.isFinite() && o.squareMm in 1.0..1000.0)
        require(o.boardMarkerMm.isFinite() && o.boardMarkerMm > 0 && o.boardMarkerMm < o.squareMm)
        val width = if (o.landscape) o.paper.heightMm else o.paper.widthMm
        val height = if (o.landscape) o.paper.widthMm else o.paper.heightMm
        val w = if (o.charuco) o.columns * o.squareMm else o.markerMm
        val h = if (o.charuco) o.rows * o.squareMm else o.markerMm
        // 10 mm white border; footer reserved for the print warning and check ruler.
        val top = 15.0; val bottom = if (o.ruler) 35.0 else 25.0
        val gap = 10.0; val label = if (o.labels) 9.0 else 0.0
        require(w <= width - 20.0 && h + label <= height - top - bottom) { "PAPER_FIT" }
        val columns = if (o.multiple && !o.charuco) floor((width - 20 + gap) / (w + gap)).toInt().coerceAtLeast(1) else 1
        val rows = if (o.multiple && !o.charuco) floor((height - top - bottom + gap) / (h + label + gap)).toInt().coerceAtLeast(1) else 1
        val ids = if (o.charuco) listOf(0) else (o.firstId..o.lastId).toList()
        val pages = ids.chunked(columns * rows).map { chunk ->
            chunk.mapIndexed { i, id ->
                val totalWidth = columns * w + (columns - 1) * gap
                Placement(id, (width - totalWidth) / 2 + (i % columns) * (w + gap), top + (i / columns) * (h + label + gap), w, h)
            }
        }
        return PrintPlan(width, height, pages)
    }
}
