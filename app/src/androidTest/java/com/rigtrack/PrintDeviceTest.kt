package com.rigtrack

import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rigtrack.printing.*
import com.rigtrack.core.model.MarkerDictionaries
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.*
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PrintDeviceTest {
    @Test fun generatedPdfsHaveExactGeometryAndDetectablePatterns() {
        assertTrue(OpenCVLoader.initLocal())
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("tr", "en")) for (board in listOf(false, true)) {
            val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale(language)) })
            val options = PrintOptions(dictionary = if (board) 0 else MarkerDictionaries.DEFAULT, firstId = if(board) 0 else 1, lastId = if(board) 0 else 1, charuco = board)
            val file = MarkerPdf.create(context, options, File(base.getExternalFilesDir(null), "android_${language}_${if (board) "charuco" else "apriltag"}.pdf"))
            val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(bytes.contains("/MediaBox [0 0 595.27559055 841.88976378]"))
            val bitmap = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { pdf ->
                assertEquals(1, pdf.pageCount)
                pdf.openPage(0).use { page -> Bitmap.createBitmap(1260, 1782, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) } }
            } }
            File(base.getExternalFilesDir(null), "android_${language}_${if (board) "charuco" else "apriltag"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val rgba = Mat(); val gray = Mat(); Utils.bitmapToMat(bitmap, rgba); Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            try {
                if (board) {
                    val charuco = CharucoBoard(Size(5.0, 7.0), .030f, .022f, Objdetect.getPredefinedDictionary(0))
                    val corners = Mat(); val ids = Mat(); val detector = CharucoDetector(charuco)
                    try { detector.detectBoard(gray, corners, ids); assertEquals(24, ids.rows()) } finally { corners.release(); ids.release(); detector.clear() }
                } else {
                    val detector = ArucoDetector(Objdetect.getPredefinedDictionary(20)); val corners = mutableListOf<Mat>(); val ids = Mat()
                    try {
                        detector.detectMarkers(gray, corners, ids); assertEquals(1, ids.rows()); assertEquals(1, ids.get(0, 0)[0].toInt())
                        val p0 = corners[0].get(0, 0); val p1 = corners[0].get(0, 1)
                        assertEquals(150.0, kotlin.math.hypot(p1[0] - p0[0], p1[1] - p0[1]) / 6, .5)
                    } finally { corners.forEach { it.release() }; ids.release(); detector.clear() }
                }
            } finally { rgba.release(); gray.release(); bitmap.recycle() }
        }
        val a3 = MarkerPdf.create(base, PrintOptions(paper = Paper.A3, markerMm = 250.0, labels = false), File(base.getExternalFilesDir(null), "android_a3_250mm.pdf"))
        assertTrue(a3.readBytes().toString(Charsets.ISO_8859_1).contains("841.88976378 1190.55118110"))
    }
}
