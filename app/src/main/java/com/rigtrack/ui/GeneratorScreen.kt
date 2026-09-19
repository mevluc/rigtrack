package com.rigtrack.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rigtrack.R
import com.rigtrack.printing.*
import kotlinx.coroutines.*
import java.io.File

@Composable fun GeneratorScreen(a: MainActivity) {
    var board by rememberSaveable { mutableStateOf(false) }; var dictionary by rememberSaveable { mutableIntStateOf(MarkerPdf.dictionaries.indexOfFirst { it.first == a.detector.dictionary }.coerceAtLeast(0)) }
    var first by rememberSaveable { mutableStateOf("0") }; var last by rememberSaveable { mutableStateOf("0") }
    var size by rememberSaveable { mutableStateOf("150") }; var paper by rememberSaveable { mutableIntStateOf(0) }
    var landscape by rememberSaveable { mutableStateOf(false) }; var multiple by rememberSaveable { mutableStateOf(false) }
    var labels by rememberSaveable { mutableStateOf(true) }; var ruler by rememberSaveable { mutableStateOf(true) }
    var columns by rememberSaveable { mutableStateOf(a.detector.boardX.toString()) }; var rows by rememberSaveable { mutableStateOf(a.detector.boardY.toString()) }
    var square by rememberSaveable { mutableStateOf(a.detector.squareMm.toString()) }; var inner by rememberSaveable { mutableStateOf(a.detector.boardMarkerMm.toString()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }; var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableIntStateOf(0) }; var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    LaunchedEffect(board, dictionary, first, last, size, paper, landscape, multiple, labels, ruler, columns, rows, square, inner) { preview = null; file = null }
    fun options() = PrintOptions(MarkerPdf.dictionaries[dictionary].first, first.toInt(), last.toInt(), size.number(10.0, 1000.0), Paper.entries[paper], landscape, multiple, labels, ruler, board, columns.toInt(), rows.toInt(), square.number(1.0, 1000.0), inner.number(0.1, 1000.0))
    fun generate(save: Boolean) { scope.launch {
        busy = true; error = 0
        runCatching {
            val o = options(); PrintLayout.plan(o)
            withContext(Dispatchers.Default) {
                val target = File(a.cacheDir, if (board) "rigtrack_charuco.pdf" else "rigtrack_markers.pdf")
                MarkerPdf.create(context, o, target)
                val bitmap = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer -> renderer.openPage(0).use { page ->
                    Bitmap.createBitmap(1000, (1000.0 * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE); page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                } } }
                target to bitmap
            }
        }.onSuccess { (target, bitmap) ->
            preview = bitmap; file = target
            if (save) a.export(target, "pdf")
        }.onFailure { a.errorDetail = it.toString(); error = if (it.message == "PAPER_FIT") R.string.paper_fit else R.string.invalid_value }
        busy = false
    } }
    Page {
        TabRow(if (board) 1 else 0) {
            Tab(!board, { board = false }, text = { Text(s(R.string.marker_tab)) })
            Tab(board, { board = true; dictionary = MarkerPdf.dictionaries.indexOfFirst { it.first == a.detector.dictionary }.coerceAtLeast(0); if (runCatching { PrintLayout.plan(options()) }.isFailure) paper = 1 }, text = { Text(s(R.string.charuco_tab)) })
        }
        Text(s(R.string.print_warning), color = RigTeal)
        SectionCard(s(if (board) R.string.charuco_tab else R.string.marker_tab)) {
            PresetSelector(s(R.string.dictionary), MarkerPdf.dictionaries.map { it.second }, dictionary) {
                dictionary = it;a.detector=a.detector.copy(dictionary=MarkerPdf.dictionaries[it].first);a.save("settings.json",a.detector)
            }
            if (board) {
                Input(R.string.board_columns, columns) { columns = it }; Input(R.string.board_rows, rows) { rows = it }
                Input(R.string.square_size, square) { square = it }; Input(R.string.board_marker_size, inner) { inner = it }
                Text(s(R.string.board_match))
                SecondaryButton(s(R.string.save)) { a.guard {
                    val o = options(); PrintLayout.plan(o)
                    a.detector = a.detector.copy(dictionary = o.dictionary, boardX = o.columns, boardY = o.rows, squareMm = o.squareMm, boardMarkerMm = o.boardMarkerMm)
                    a.save("settings.json", a.detector); a.notice = R.string.saved
                } }
            } else {
                Input(R.string.first_id, first) { first = it }; Input(R.string.last_id, last) { last = it }
                PresetSelector(s(R.string.marker_size), listOf("100", "150", "200", "250", "300", s(R.string.advanced)), listOf("100", "150", "200", "250", "300").indexOf(size).let { if (it < 0) 5 else it }) { if (it < 5) size = listOf("100", "150", "200", "250", "300")[it] else size = "" }
                Input(R.string.marker_size, size, Modifier.testTag("marker_mm")) { size = it }; SettingRow(s(R.string.multi_page), multiple) { multiple = it }
            }
        }
        SectionCard(s(R.string.paper)) {
            PresetSelector(s(R.string.paper), listOf("A4 · 210 × 297 mm", "A3 · 297 × 420 mm"), paper) { paper = it }
            PresetSelector(s(R.string.orientation), listOf(s(R.string.portrait), s(R.string.landscape)), if (landscape) 1 else 0) { landscape = it == 1 }
            SettingRow(s(R.string.labels), labels) { labels = it }; SettingRow(s(R.string.ruler), ruler) { ruler = it }
        }
        if (error != 0) Text(s(error), color = RigRed)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(s(R.string.preview), Modifier.weight(1f).testTag("pdf_preview"), !busy) { generate(false) }
            PrimaryButton(s(R.string.save_pdf), Modifier.weight(1f), !busy) { generate(true) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        preview?.let { Image(it.asImageBitmap(), s(R.string.preview), Modifier.fillMaxWidth().heightIn(max = 640.dp)) }
    }
}
