package com.rigtrack.printing

import android.content.Context
import android.graphics.*
import com.rigtrack.R
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.objdetect.*
import java.io.*
import java.util.Locale
import java.util.zip.DeflaterOutputStream

/** Vector marker cells and exact floating-point ISO page sizes, with localized raster labels.
 * No print geometry is derived from screen density or bitmap DPI. */
object MarkerPdf {
    val dictionaries = com.rigtrack.core.model.MarkerDictionaries.all.map { it.id to it.label }
    fun capacity(dictionary: Int): Int {
        val bytes = Objdetect.getPredefinedDictionary(dictionary).get_bytesList()
        return try { bytes.rows() } finally { bytes.release() }
    }
    fun create(context: Context, options: PrintOptions, output: File): File {
        check(OpenCVLoader.initLocal())
        val plan = PrintLayout.plan(options)
        require(options.dictionary in dictionaries.map { it.first })
        val dictionary = Objdetect.getPredefinedDictionary(options.dictionary)
        require(if (options.charuco) options.columns * options.rows / 2 <= capacity(options.dictionary) else options.lastId < capacity(options.dictionary))
        val pdf = PdfObjects(); val catalog = pdf.reserve(); val pages = pdf.reserve(); val pageIds = mutableListOf<Int>()
        for (placements in plan.pages) {
            val content = StringBuilder("0 g\n"); val images = mutableListOf<Pair<String, Int>>()
            fun rect(x: Double, y: Double, w: Double, h: Double) { content.append("${f(PrintLayout.points(x))} ${f(PrintLayout.points(plan.heightMm - y - h))} ${f(PrintLayout.points(w))} ${f(PrintLayout.points(h))} re f\n") }
            fun marker(id: Int, x: Double, y: Double, mm: Double) {
                val cells = dictionary.get_markerSize() + 2; val mat = Mat()
                try {
                    Objdetect.generateImageMarker(dictionary, id, cells, mat, 1)
                    val pixels = ByteArray(cells * cells); mat.get(0, 0, pixels)
                    for (row in 0 until cells) { var col = 0; while (col < cells) {
                        if (pixels[row * cells + col].toInt() and 255 < 128) {
                            val start = col; while (col < cells && (pixels[row * cells + col].toInt() and 255) < 128) col++
                            rect(x + start * mm / cells, y + row * mm / cells, (col - start) * mm / cells, mm / cells)
                        } else col++
                    } }
                } finally { mat.release() }
            }
            fun label(text: String, x: Double, y: Double, width: Double, height: Double) {
                val bitmap = Bitmap.createBitmap(1600, (1600 * height / width).toInt().coerceAtLeast(50), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE)
                val pixelsPerMm = 1600f / width.toFloat()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 3f * pixelsPerMm }
                val lines = mutableListOf<String>(); var line = ""
                for (word in text.split(' ')) { val next = if (line.isEmpty()) word else "$line $word"; if (paint.measureText(next) > 1560 && line.isNotEmpty()) { lines += line; line = word } else line = next }
                if (line.isNotEmpty()) lines += line
                lines.forEachIndexed { i, value -> canvas.drawText(value, .5f * pixelsPerMm, paint.textSize * (1.1f + i * 1.2f), paint) }
                val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val gray = ByteArray(pixels.size) { (pixels[it] and 255).toByte() }
                val id = pdf.stream("/Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} /ColorSpace /DeviceGray /BitsPerComponent 8 /Interpolate false", gray)
                bitmap.recycle(); val name = "Im${images.size}"; images += name to id
                content.append("q ${f(PrintLayout.points(width))} 0 0 ${f(PrintLayout.points(height))} ${f(PrintLayout.points(x))} ${f(PrintLayout.points(plan.heightMm - y - height))} cm /$name Do Q\n")
            }
            for (p in placements) {
                if (options.charuco) {
                    val board = CharucoBoard(Size(options.columns.toDouble(), options.rows.toDouble()), options.squareMm.toFloat(), options.boardMarkerMm.toFloat(), dictionary)
                    val objects = board.objPoints; val ids = board.ids
                    try {
                        val markerCells = objects.map { points -> val point = points.toArray()[0]; (point.x / options.squareMm).toInt() to (point.y / options.squareMm).toInt() }.toSet()
                        for (row in 0 until options.rows) for (col in 0 until options.columns) if ((col to row) !in markerCells) rect(p.xMm + col * options.squareMm, p.yMm + row * options.squareMm, options.squareMm, options.squareMm)
                        objects.forEachIndexed { i, points ->
                            val point = points.toArray()[0]; val col = (point.x / options.squareMm).toInt(); val row = (point.y / options.squareMm).toInt(); val margin = (options.squareMm - options.boardMarkerMm) / 2
                            marker(ids.toArray()[i], p.xMm + col * options.squareMm + margin, p.yMm + row * options.squareMm + margin, options.boardMarkerMm)
                        }
                    } finally { objects.forEach { it.release() }; ids.release() }
                } else marker(p.id, p.xMm, p.yMm, p.widthMm)
                if (options.labels) label(if (options.charuco) "ChArUco ${options.columns} × ${options.rows} · ${options.squareMm} / ${options.boardMarkerMm} mm · ${dictionaries.first { it.first == options.dictionary }.second}" else "${dictionaries.first { it.first == options.dictionary }.second} · ID ${p.id} · ${p.widthMm} mm", p.xMm, p.yMm + p.heightMm + 2, p.widthMm, 7.0)
            }
            label(context.getString(R.string.print_warning), 10.0, plan.heightMm - 23, plan.widthMm - 20, 15.0)
            if (options.ruler) {
                val y = plan.heightMm - 32; rect(10.0, y, 100.0, .25)
                for (i in 0..10) rect(10.0 + i * 10, y - if (i % 5 == 0) 3 else 2, .2, if (i % 5 == 0) 3.0 else 2.0)
                label("100 mm", 115.0, y - 4, 30.0, 6.0)
            }
            val stream = pdf.stream("", content.toString().toByteArray(Charsets.US_ASCII))
            pageIds += pdf.add("<< /Type /Page /Parent $pages 0 R /MediaBox [0 0 ${f(PrintLayout.points(plan.widthMm))} ${f(PrintLayout.points(plan.heightMm))}] /Resources << /XObject << ${images.joinToString(" ") { "/${it.first} ${it.second} 0 R" }} >> >> /Contents $stream 0 R >>")
        }
        pdf.set(pages, "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>")
        pdf.set(catalog, "<< /Type /Catalog /Pages $pages 0 R /ViewerPreferences << /PrintScaling /None >> >>")
        output.outputStream().use { pdf.write(it, catalog) }; return output
    }
    private fun f(value: Double) = String.format(Locale.US, "%.8f", value)
    private class PdfObjects {
        val objects = mutableListOf<ByteArray>()
        fun reserve(): Int { objects += byteArrayOf(); return objects.size }
        fun set(id: Int, text: String) { objects[id - 1] = text.toByteArray(Charsets.US_ASCII) }
        fun add(text: String): Int = reserve().also { set(it, text) }
        fun stream(extra: String, bytes: ByteArray): Int {
            val compressed = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(bytes) } }.toByteArray()
            objects += "<< $extra /Filter /FlateDecode /Length ${compressed.size} >>\nstream\n".toByteArray() + compressed + "\nendstream".toByteArray()
            return objects.size
        }
        fun write(output: OutputStream, root: Int) {
            val out = ByteArrayOutputStream(); out.write("%PDF-1.4\n%RigTrack\n".toByteArray()); val offsets = mutableListOf<Int>()
            objects.forEachIndexed { index, bytes -> offsets += out.size(); out.write("${index + 1} 0 obj\n".toByteArray()); out.write(bytes); out.write("\nendobj\n".toByteArray()) }
            val xref = out.size(); out.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray())
            offsets.forEach { out.write(String.format(Locale.US, "%010d 00000 n \n", it).toByteArray()) }
            out.write("trailer\n<< /Size ${objects.size + 1} /Root $root 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray()); out.writeTo(output)
        }
    }
}
