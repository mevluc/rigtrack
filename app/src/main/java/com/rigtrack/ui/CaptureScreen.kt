package com.rigtrack.ui

import android.hardware.Sensor
import android.os.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rigtrack.R
import com.rigtrack.BuildConfig
import com.rigtrack.data.Preferences
import com.rigtrack.core.model.MarkerDictionaries
import com.rigtrack.recording.ReferenceVideoStatus
import kotlinx.coroutines.*
import java.util.Locale

internal fun qualityLabel(score: Int): Int = when { score >= 90 -> R.string.excellent; score >= 70 -> R.string.good; score >= 50 -> R.string.fair; score > 0 -> R.string.poor; else -> R.string.lost }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun CaptureScreen(a: MainActivity, prefs: Preferences, back: () -> Unit) {
    val c = a.capture.controller ?: return
    var tick by remember { mutableIntStateOf(0) }; var diagnostics by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }; var markers by remember { mutableStateOf(prefs.markers) }; var axes by remember { mutableStateOf(prefs.axes) }
    var flash by remember { mutableStateOf(false) }
    SideEffect { a.capture.overlay?.showMarkers = markers; a.capture.overlay?.showAxes = axes; a.capture.overlay?.mappedIds = c.markerMap.references.map { it.id }.toSet() }
    DisposableEffect(c) { val job = a.scope.launch { while (isActive) {
        tick++
        if (c.writer != null && (a.repo.sessions.usableSpace < 100L * 1024 * 1024 || c.writer?.failure != null)) {
            a.error = if (a.repo.sessions.usableSpace < 100L * 1024 * 1024) R.string.storage_critical else R.string.error_generic
            a.finishRecording()
        }
        delay(250)
    } }; onDispose { job.cancel() } }
    @Suppress("UNUSED_VARIABLE") val observed = tick
    val sample = c.latest; val writer = c.writer; val score = sample?.qualityScore?.toInt() ?: 0
    val duration = if (writer == null) 0.0 else (SystemClock.elapsedRealtimeNanos() - writer.startNs) / 1e9
    BoxWithConstraints(Modifier.fillMaxSize().background(RigBackground)) {
        val wide = maxWidth > maxHeight
        a.viewport?.let { view -> AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
        if (a.shot.synthetic) Column(Modifier.align(Alignment.Center).padding(end = if (wide) 252.dp else 0.dp, top = if (wide) 88.dp else 0.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Animation, null, Modifier.size(72.dp), tint = RigTeal)
            Text(s(R.string.synthetic), color = RigTeal, style = MaterialTheme.typography.headlineMedium)
            Text(s(R.string.synthetic_hint), color = Color.White)
        }
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(end = if (wide) 252.dp else 0.dp).background(RigBackground.copy(alpha = .84f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s(R.string.back)) }
                Column(Modifier.weight(1f)) { Text(a.shot.name, style = MaterialTheme.typography.titleMedium); Text(a.shot.film.name, style = MaterialTheme.typography.labelSmall) }
                StatusChip("%02d:%02d".format(Locale.US, duration.toInt() / 60, duration.toInt() % 60), writer == null)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(s(qualityLabel(score)), score >= 50)
                when (a.capture.referenceVideoState.status) {
                    ReferenceVideoStatus.RECORDING -> StatusChip(s(R.string.reference_video_recording))
                    ReferenceVideoStatus.FAILED -> StatusChip(s(R.string.reference_video), false)
                    else -> Unit
                }
                if (prefs.stats) {
                    StatusChip("%.0f FPS".format(Locale.US, c.arRate.hz()))
                    StatusChip("${s(R.string.markers)} ${c.markers.size}")
                }
            }
            if (a.capture.referenceVideoState.status == ReferenceVideoStatus.FAILED) Text(s(R.string.reference_video_failed), color = RigRed, style = MaterialTheme.typography.bodySmall)
            Text(s(if (sample?.worldAnchor == null) R.string.origin_missing else if (score >= 70) R.string.quality_good else R.string.quality_tip), style = MaterialTheme.typography.bodySmall)
        }
        val controls = if (wide) Modifier.width(240.dp).fillMaxHeight().align(Alignment.CenterEnd).verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        Column(controls.background(RigBackground.copy(alpha = .90f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.captureMode == "calibration") {
                val cal = c.markerWorker?.calibration
                Text("${s(R.string.samples)} ${cal?.count ?: 0} / 25", color = RigTeal)
                LinearProgressIndicator(progress = { (cal?.count ?: 0).coerceAtMost(25) / 25f }, modifier = Modifier.fillMaxWidth())
                val feedback = cal?.feedback.orEmpty()
                Text(s(when {
                    feedback.contains("resolution", true) -> R.string.cal_restart
                    feedback.contains("small", true) -> R.string.cal_closer
                    feedback.contains("angle", true) || feedback.contains("similar", true) -> R.string.cal_angle
                    (cal?.count ?: 0) >= 15 -> R.string.cal_ready
                    feedback.contains("Good") -> R.string.cal_good
                    else -> R.string.cal_more
                }), style = MaterialTheme.typography.bodySmall)
                PrimaryButton(s(R.string.solve_calibration), enabled = (cal?.count ?: 0) >= 15) { a.scope.launch {
                    runCatching { withContext(Dispatchers.Default) { cal!!.calibrate("${Build.MODEL} ${System.currentTimeMillis()}") } }.onSuccess { profile ->
                        a.save("calibrations.json", a.repo.calibrations().filter { it.key != profile.key } + profile); a.notice = R.string.calibration_apply
                    }.onFailure { a.errorDetail = it.toString(); a.error = R.string.cal_bad }
                } }
            }
            if (a.captureMode == "map") {
                val worker=c.markerWorker
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    StatusChip(MarkerDictionaries.label(c.detectorSettings.dictionary))
                    StatusChip("${s(R.string.detected_candidates)} ${worker?.lastDetectedCandidateCount?:0}")
                    StatusChip("${s(R.string.samples)} ${c.mapCalibration.counts().values.sum()}")
                }
                Text("${s(R.string.samples)} · ${c.mapCalibration.counts()}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(s(if (c.calibratingMap) R.string.stop else R.string.collect), Modifier.weight(1f), enabled=c.calibratingMap||c.markers.isNotEmpty()) { a.guard {
                        check(c.latest?.valid == true) { "SET ORIGIN" }; check(c.markerMap.references.isEmpty() || c.worldLock.active) { "Show a mapped marker" }; c.calibratingMap = !c.calibratingMap
                    } }
                    PrimaryButton(s(R.string.save_map), Modifier.weight(1f)) { a.guard {
                        val map = c.mapCalibration.build(c.markerMap.name, a.detector, c.markerMap)
                        c.markerMap = map; a.selectedMap = map; c.calibratingMap = false; c.worldLock.active = true
                        a.save("maps.json", a.repo.maps().filter { it.name != map.name } + map); a.notice = R.string.saved
                    } }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(markers, { markers = !markers; a.capture.overlay?.showMarkers = markers }, { Text(s(R.string.markers)) })
                FilterChip(axes, { axes = !axes; a.capture.overlay?.showAxes = axes }, { Text(s(R.string.axes)) })
                AssistChip({ diagnostics = true }, { Text(s(R.string.diagnostics)) })
                if (!a.shot.synthetic) AssistChip({ tools = true }, { Text(s(R.string.tools)) })
            }
            val originButton: @Composable (Modifier) -> Unit = { modifier ->
                TextButton({ a.guard { a.capture.origin() } }, modifier.testTag("origin"), contentPadding = PaddingValues(4.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CenterFocusStrong, null); Text(s(R.string.origin), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            }
            val recordButton: @Composable () -> Unit = {
                if (a.captureMode == "record") {
                    val label = s(if (writer == null) R.string.record else R.string.stop)
                    Box(Modifier.size(76.dp).border(2.dp, Color.White, CircleShape).padding(7.dp).background(RigRed, CircleShape)
                        .clickable(enabled = !c.stopping) { a.guard { if (c.writer == null) a.capture.start() else a.finishRecording(); tick++ } }
                        .semantics { contentDescription = label; role = Role.Button }.testTag("record"), contentAlignment = Alignment.Center) {
                        if (writer != null) Box(Modifier.size(25.dp).background(Color.White, RoundedCornerShape(4.dp)))
                    }
                }
            }
            val syncButton: @Composable (Modifier) -> Unit = { modifier ->
                TextButton({ a.guard { a.capture.sync(prefs.sound, prefs.haptic); a.scope.launch { flash = true; delay(120); flash = false } } }, modifier.testTag("sync"), enabled = writer != null, contentPadding = PaddingValues(4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.GraphicEq, null); Text(s(R.string.sync)) }
                }
            }
            if (wide) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { recordButton() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { originButton(Modifier.weight(1f)); syncButton(Modifier.weight(1f)) }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                originButton(Modifier.weight(1f)); recordButton(); syncButton(Modifier.weight(1f))
            }
        }
        if (flash) Box(Modifier.fillMaxSize().background(Color.White))
        if (a.capture.referenceVideoState.status == ReferenceVideoStatus.FINALIZING) Box(Modifier.fillMaxSize().background(RigBackground.copy(alpha = .88f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text(s(R.string.saving_shot)) }
        }
    }
    if (diagnostics) ModalBottomSheet(onDismissRequest = { diagnostics = false }) { DiagnosticsScreen(a) }
    if (tools) ModalBottomSheet(onDismissRequest = { tools = false }) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PrimaryButton(s(R.string.relocalize), enabled = writer == null) { a.guard { a.capture.relocalize(); tools = false } }
        SettingRow(s(R.string.board_reference), c.markerWorker?.boardReferenceMode == true) { c.markerWorker?.boardReferenceMode = it; tick++ }
        Text(s(R.string.map_instruction)); Spacer(Modifier.height(16.dp))
    } }
}

@Composable fun DiagnosticsScreen(a: MainActivity) = Page {
    val c = a.capture.controller
    SectionCard(s(R.string.diagnostics)) {
        InfoRow(s(R.string.about), "RigTrack ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow(s(R.string.free_storage), "${a.repo.sessions.usableSpace / 1048576} MB")
        val power = LocalContext.current.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        InfoRow(s(R.string.thermal), s(if (Build.VERSION.SDK_INT >= 29) listOf(R.string.thermal_none, R.string.thermal_light, R.string.thermal_moderate, R.string.thermal_severe, R.string.thermal_critical, R.string.thermal_emergency, R.string.thermal_shutdown).getOrElse(power.currentThermalStatus) { R.string.unavailable } else R.string.unavailable))
        InfoRow(s(R.string.ar_rate), "%.1f FPS".format(Locale.US, c?.arRate?.hz() ?: 0.0))
        InfoRow(s(R.string.gyro), "%.1f Hz".format(Locale.US, c?.imu?.rates?.get(Sensor.TYPE_GYROSCOPE)?.hz() ?: 0.0))
        InfoRow(s(R.string.accel), "%.1f Hz".format(Locale.US, c?.imu?.rates?.get(Sensor.TYPE_ACCELEROMETER)?.hz() ?: 0.0))
        InfoRow(s(R.string.detector_rate), "%.1f FPS".format(Locale.US, c?.markerWorker?.rate?.hz() ?: 0.0))
        InfoRow(s(R.string.dictionary), MarkerDictionaries.label(c?.detectorSettings?.dictionary ?: a.detector.dictionary))
        InfoRow(s(R.string.cpu_image), "${c?.markerWorker?.lastImageWidth ?: 0} × ${c?.markerWorker?.lastImageHeight ?: 0} · %.1f FPS".format(Locale.US,c?.markerWorker?.inputRate?.hz()?:0.0))
        InfoRow(s(R.string.detected_candidates), "${c?.markerWorker?.lastDetectedCandidateCount ?: 0}")
        InfoRow(s(R.string.rejected_candidates), "${c?.markerWorker?.lastRejectedCandidateCount ?: 0}")
        InfoRow(s(R.string.detected_ids), c?.markerWorker?.lastDetectedIds?.joinToString().orEmpty().ifBlank{"—"})
        InfoRow(s(R.string.submitted_frames), "${c?.markerWorker?.submitted?.get() ?: 0}")
        InfoRow(s(R.string.processed_frames), "${c?.markerWorker?.processed?.get() ?: 0}")
        InfoRow(s(R.string.dropped), "${c?.markerWorker?.dropped?.get() ?: 0}")
        InfoRow(s(R.string.cpu_unavailable), "${c?.missingImages?.get() ?: 0}")
        InfoRow(s(R.string.processing_time), "%.2f ms".format(Locale.US,c?.markerWorker?.lastProcessingMs?:0.0))
        InfoRow(s(R.string.intrinsics_match), s(if(c?.markerWorker?.lastIntrinsicsMatchedImage==true)R.string.enabled else R.string.disabled))
        InfoRow(s(R.string.world_lock), s(if (c?.worldLock?.active == true) R.string.enabled else R.string.disabled))
    }
    var technical by remember { mutableStateOf(false) }
    SecondaryButton(s(R.string.technical_details)) { technical = !technical }
    if (technical) SectionCard(s(R.string.technical_details)) {
        Text(a.errorDetail.ifBlank { c?.lastError.orEmpty() })
        Text("AR ${c?.writer?.arCount?.get() ?: 0} · IMU ${c?.writer?.imuCount?.get() ?: 0} · ${c?.latest?.state.orEmpty()} ${c?.latest?.failure.orEmpty()}")
        InfoRow(s(R.string.cpu_image_timestamp),"${c?.markerWorker?.lastImageTimestampNs?:0}")
        InfoRow(s(R.string.pose_timestamp),"${c?.markerWorker?.lastPoseTimestampNs?:0}")
        InfoRow(s(R.string.last_detector_error),c?.markerWorker?.lastException.orEmpty().ifBlank{"—"})
        a.repo.calibrations().forEach { Text("${it.name}\n${it.key}\nRMS ${it.rms} px · ${it.samples}") }
    }
    Text(s(R.string.offline_notice)); Text(s(R.string.sync_notice))
}
