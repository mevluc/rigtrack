package com.rigtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.*
import com.rigtrack.R
import com.rigtrack.BuildConfig
import com.rigtrack.core.model.ReferenceVideoQuality
import com.rigtrack.core.model.ShotSettings
import com.rigtrack.data.Preferences
import com.rigtrack.data.RecordingQuality
import com.rigtrack.export.Recovery
import com.rigtrack.export.TrackExporter
import com.rigtrack.recording.ReferenceVideoRecorder
import kotlinx.coroutines.*
import java.io.File
import java.text.DateFormat
import java.util.*

@Composable fun SettingsScreen(a: MainActivity, prefs: Preferences) = Page {
    SectionCard(s(R.string.general)) {
        PresetSelector(s(R.string.language), listOf("Türkçe", "English"), if (prefs.language == "tr") 0 else 1) { a.scope.launch { a.preferences.language(if (it == 0) "tr" else "en") } }
        SettingRow(s(R.string.keep_awake), prefs.keepAwake) { a.scope.launch { a.preferences.flag("keep_awake", it) } }
    }
    SectionCard(s(R.string.recording_settings)) {
        val videoQualities = listOf(ReferenceVideoQuality.OFF, ReferenceVideoQuality.P720, ReferenceVideoQuality.P1080)
        PresetSelector(s(R.string.reference_video), listOf(s(R.string.reference_video_off), s(R.string.reference_video_720p), s(R.string.reference_video_1080p)), videoQualities.indexOf(a.shot.referenceVideoQuality ?: ReferenceVideoQuality.P720).coerceAtLeast(0)) {
            a.shot = a.shot.copy(referenceVideoQuality = videoQualities[it]); a.save("shot_defaults.json", a.shot)
        }
        SettingRow(s(R.string.sound), prefs.sound) { a.scope.launch { a.preferences.flag("sound", it) } }
        SettingRow(s(R.string.haptic), prefs.haptic) { a.scope.launch { a.preferences.flag("haptic", it) } }
        Text(s(R.string.sync_notice), style = MaterialTheme.typography.bodySmall)
    }
    SectionCard(s(R.string.tracking)) {
        val d = a.detector
        val rates = listOf(5, 10, 15, 20, 30)
        PresetSelector(s(R.string.marker_rate), rates.map { "$it" }, rates.indexOf(d.fps).coerceAtLeast(0)) { a.detector = a.detector.copy(fps = rates[it]); a.save("settings.json", a.detector) }
        PresetSelector(s(R.string.ar_fps), listOf(s(R.string.auto), "30", "60"), listOf(0, 30, 60).indexOf(d.preferredFps).coerceAtLeast(0)) { a.detector = a.detector.copy(preferredFps = listOf(0, 30, 60)[it]); a.save("settings.json", a.detector) }
        val smoothing = listOf("Off", "Low", "Medium", "High")
        PresetSelector(s(R.string.smoothing), listOf(s(R.string.off), s(R.string.low), s(R.string.medium), s(R.string.high)), smoothing.indexOf(a.shot.smoothing).coerceAtLeast(0)) { a.shot = a.shot.copy(smoothing = smoothing[it]); a.save("shot_defaults.json", a.shot) }
        val dictionaries = listOf(20 to "AprilTag 36h11", 0 to "ArUco 4×4 / 50", 1 to "ArUco 4×4 / 100", 4 to "ArUco 5×5 / 50", 5 to "ArUco 5×5 / 100", 8 to "ArUco 6×6 / 50", 9 to "ArUco 6×6 / 100", 12 to "ArUco 7×7 / 50")
        PresetSelector(s(R.string.dictionary), dictionaries.map { it.second }, dictionaries.indexOfFirst { it.first == d.dictionary }.coerceAtLeast(0)) { a.detector = a.detector.copy(dictionary = dictionaries[it].first); a.save("settings.json", a.detector) }
        val values = remember { mutableStateListOf("${d.markerSizeMm}", "${d.minPixelArea}", "${d.maxError}", "${d.correctionStrength}", "${d.minConfidence}", "${a.shot.gapMaxMs}", "${d.sensorPeriodUs}") }
        listOf(R.string.marker_size, R.string.min_area, R.string.max_error, R.string.correction, R.string.confidence, R.string.gap, R.string.sensor_period).forEachIndexed { i, label -> Input(label, values[i]) { values[i] = it } }
        PrimaryButton(s(R.string.save)) { a.guard {
            a.detector = a.detector.copy(markerSizeMm = values[0].number(1.0, 2000.0), minPixelArea = values[1].number(16.0, 1000000.0), maxError = values[2].number(.1, 20.0), correctionStrength = values[3].number(0.0, 1.0), minConfidence = values[4].number(0.0, 1.0), sensorPeriodUs = values[6].number(0.0, 1000000.0).toInt())
            a.shot = a.shot.copy(gapMaxMs = values[5].number(1.0, 1000.0).toInt()); a.save("settings.json", a.detector); a.save("shot_defaults.json", a.shot); a.notice = R.string.saved
        } }
    }
    SectionCard(s(R.string.interface_settings)) {
        SettingRow(s(R.string.markers), prefs.markers) { a.scope.launch { a.preferences.flag("markers", it) } }
        SettingRow(s(R.string.axes), prefs.axes) { a.scope.launch { a.preferences.flag("axes", it) } }
        SettingRow(s(R.string.stats), prefs.stats) { a.scope.launch { a.preferences.flag("stats", it) } }
    }
    SectionCard(s(R.string.advanced)) {
        SettingRow(s(R.string.developer), prefs.developer) { a.scope.launch { a.preferences.flag("developer", it) } }
        SettingRow(s(R.string.logging), a.detector.debug) { a.detector = a.detector.copy(debug = it); a.save("settings.json", a.detector) }
    }
    SectionCard(s(R.string.about)) {
        Text("RigTrack"); Text(s(R.string.about_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SecondaryButton(s(R.string.about), Modifier.fillMaxWidth()) { a.go("about") }
        SecondaryButton(s(R.string.reopen_onboarding)) { a.scope.launch { a.preferences.flag("onboarding_done", false) } }
    }
}

@Composable fun AboutScreen(a: MainActivity) = Page {
    SectionCard("") {
        Text("RigTrack", style = MaterialTheme.typography.headlineMedium)
        InfoRow(s(R.string.version_label), BuildConfig.VERSION_NAME)
        Text(s(R.string.about_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(s(R.string.developer_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Mevlüt Çetin", style = MaterialTheme.typography.titleLarge)
    }
    Text(s(R.string.offline_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun metadata(file: File) = runCatching { JsonParser.parseString(File(file, "metadata.json").readText()).asJsonObject }.getOrNull()
private fun shot(file: File) = runCatching { Gson().fromJson(metadata(file)?.get("shot"), ShotSettings::class.java) }.getOrNull()
@Composable private fun recordedQuality(file: File): Int? {
    val quality by produceState<Int?>(null, file) { value = withContext(Dispatchers.IO) { RecordingQuality.average(file) } }
    return quality
}

@Composable fun RecordingsScreen(a: MainActivity) = Page {
    val files = remember { a.repo.recordings() }
    if (files.isEmpty()) EmptyState(s(R.string.empty_recordings), s(R.string.empty_recordings_hint))
    files.forEach { file ->
        val config = remember(file) { shot(file) }; val meta = remember(file) { metadata(file) }
        SectionCard(config?.name ?: file.name) {
            ReferenceVideoThumbnail(file, Modifier.fillMaxWidth().height(112.dp))
            Text("${config?.film?.name.orEmpty()} · ${config?.film?.fps?.let { it.numerator.toDouble() / it.denominator } ?: 0} FPS", style = MaterialTheme.typography.bodyMedium)
            InfoRow(s(R.string.duration), "%.2f s".format(Locale.US, meta?.get("duration_s")?.asDouble ?: 0.0))
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified())), style = MaterialTheme.typography.bodySmall)
            StatusChip(s(if (File(file, "exported.flag").exists()) R.string.exported else R.string.not_exported))
            recordedQuality(file)?.let { StatusChip("${s(R.string.quality)} · ${s(qualityLabel(it))}", it >= 50) }
            if (File(file, ReferenceVideoRecorder.FILENAME).isFile) StatusChip(s(R.string.reference_video))
            PrimaryButton(s(if (file.name.endsWith(".partial")) R.string.recover else R.string.details)) { a.selected = file; a.go("summary") }
        }
    }
    PrimaryButton(s(R.string.new_shot)) { a.go("new") }
}

@Composable fun SummaryScreen(a: MainActivity) {
    val file = a.selected ?: return
    val userPrefs by a.preferences.state.collectAsState(initial=Preferences())
    var rename by remember { mutableStateOf(false) }; var delete by remember { mutableStateOf(false) }; var exportOptions by remember { mutableStateOf(false) }; var revision by remember { mutableIntStateOf(0) }
    val config = remember(file, revision) { shot(file) }; val meta = remember(file, revision) { metadata(file) }
    var name by remember(file) { mutableStateOf(config?.name ?: file.name) }; var details by remember { mutableStateOf(false) }
    val diagnostics = remember(file) { runCatching { JsonParser.parseString(File(file, "diagnostics.json").readText()).asJsonObject }.getOrNull() }
    val referenceVideo = remember(file) { File(file, ReferenceVideoRecorder.FILENAME) }
    val hasReferenceVideo = referenceVideo.isFile && referenceVideo.length() > 0
    fun number(key: String, digits: Int = 1): String = diagnostics?.get(key)?.takeUnless { it.isJsonNull }?.let { runCatching { String.format(Locale.US, "%.${digits}f", it.asDouble) }.getOrDefault("—") } ?: "—"
    Page {
        Text(config?.name ?: file.name, style = MaterialTheme.typography.headlineMedium)
        Text(config?.film?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified())), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (diagnostics == null) Text(s(R.string.unfinished))
        else {
            SectionCard(s(R.string.quality)) {
                val quality = recordedQuality(file)
                if (quality != null) QualityIndicator(quality, s(qualityLabel(quality))) else Text(s(R.string.unavailable))
                InfoRow(s(R.string.duration), "${number("duration_s")} s")
                InfoRow(s(R.string.ar_rate), "${number("ar_average_fps")} FPS")
                InfoRow(s(R.string.detector_rate), "${number("marker_detection_fps")} FPS")
            }
        }
        if (hasReferenceVideo) SectionCard(s(R.string.reference_video)) {
            ReferenceVideoThumbnail(file, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            PrimaryButton(s(R.string.watch_reference_video), Modifier.fillMaxWidth().testTag("watch_reference_video")) { a.go("video") }
        }
        if(diagnostics!=null)SectionCard(s(R.string.tracking_refinement)){
            InfoRow(s(R.string.track_quality),"${number("track_quality_percent",1)}%")
            InfoRow(s(R.string.raw_jitter),"${number("raw_translation_jitter_mm",2)} mm · ${number("raw_rotation_jitter_deg",3)}°")
            InfoRow(s(R.string.fused_jitter),"${number("fused_translation_jitter_mm",2)} mm · ${number("fused_rotation_jitter_deg",3)}°")
            InfoRow(s(R.string.refined_jitter),"${number("refined_translation_jitter_mm",2)} mm · ${number("refined_rotation_jitter_deg",3)}°")
            InfoRow(s(R.string.rejected_outliers),number("refinement_rejected_outliers",0));InfoRow(s(R.string.rejected_marker_observations),number("rejected_corrections",0))
            InfoRow(s(R.string.repaired_samples),number("refinement_repaired_samples",0));InfoRow(s(R.string.repaired_gaps),number("refinement_repaired_gaps",0))
            InfoRow(s(R.string.longest_repaired_gap),"${number("refinement_longest_repaired_gap_ms",1)} ms");InfoRow(s(R.string.unrepaired_gaps),number("refinement_unrepaired_gaps",0))
        }
        if (file.name.endsWith(".partial")) PrimaryButton(s(R.string.recover), Modifier.fillMaxWidth()) { a.scope.launch {
            runCatching { withContext(Dispatchers.IO) { Recovery.recover(file) } }.onSuccess { a.selected = it }.onFailure(a::report)
        } } else {
            SectionCard(s(R.string.export_section)) {
                PrimaryButton(s(R.string.export), Modifier.fillMaxWidth().testTag("export"), enabled = !a.exporting) { exportOptions = true }
                SecondaryButton(s(R.string.export_csv), Modifier.fillMaxWidth()) { a.export(file, "csv") }
                if(a.exporting){LinearProgressIndicator(Modifier.fillMaxWidth());Text(s(R.string.exporting),style=MaterialTheme.typography.bodySmall)}
            }
        }
        SectionCard(s(R.string.shot_actions)) {
            SummaryAction(Icons.Default.Info, s(R.string.view_details)) { details = !details }
            SummaryAction(Icons.Default.ControlPointDuplicate, s(R.string.new_shot_same_settings)) { config?.let { a.duplicateShot(it) } }
            SummaryAction(Icons.Default.Edit, s(R.string.rename)) { rename = true }
        }
        if (details && diagnostics != null) SectionCard(s(R.string.diagnostics)) {
            InfoRow(s(R.string.detector_rate), "${number("marker_detection_fps")} FPS")
            InfoRow(s(R.string.markers), number("marker_observations", 0)); InfoRow(s(R.string.dropped), number("dropped_marker_frames", 0))
            InfoRow(s(R.string.max_error), "${number("maximum_reprojection_error", 3)} px")
            InfoRow(s(R.string.lost), "${number("tracking_lost_duration_s", 3)} s")
            InfoRow(s(R.string.film_frames), number("export_frame_count", 0))
            InfoRow(s(R.string.invalid_frames), number("invalid_export_frames", 0))
            InfoRow(s(R.string.world_corrections), number("world_lock_corrections", 0))
            InfoRow(s(R.string.relocks),number("marker_relocks",0));InfoRow(s(R.string.average_processing),"${number("average_marker_processing_ms",2)} ms")
            val moved=diagnostics.get("moved_marker_ids")?.takeUnless{it.isJsonNull}?.toString()?:"[]";InfoRow(s(R.string.moved_markers),moved)
            InfoRow(s(R.string.mean_error), "${number("average_reprojection_error", 3)} px")
            InfoRow(s(R.string.sample_counts),"${number("raw_sample_count",0)} / ${number("fused_sample_count",0)} / ${number("refined_sample_count",0)}")
            InfoRow(s(R.string.maximum_deviation),"${number("refinement_maximum_translation_deviation_mm",2)} mm · ${number("refinement_maximum_rotation_deviation_deg",3)}°")
            if (hasReferenceVideo) {
                InfoRow(s(R.string.video_resolution), meta?.get("referenceVideoResolution")?.asString ?: "—")
                InfoRow(s(R.string.video_frame_rate), meta?.get("referenceVideoFps")?.takeUnless { it.isJsonNull }?.asString ?: "—")
            }
        }
        SectionCard(s(R.string.danger_zone)) {
            OutlinedButton({ delete = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = RigRed)) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text(s(R.string.delete))
            }
        }
        PrimaryButton("+ ${s(R.string.new_shot)}", Modifier.fillMaxWidth().testTag("summary_new_shot")) { a.go("new") }
    }
    if (delete) ConfirmationDialog(s(R.string.delete_title), s(if (hasReferenceVideo) R.string.reference_video_delete_body else R.string.delete_body), s(R.string.delete), s(R.string.cancel), {
        delete = false; a.scope.launch { runCatching { withContext(Dispatchers.IO) {
            require(file.canonicalFile.parentFile == a.repo.sessions.canonicalFile); check(file.deleteRecursively())
        } }.onSuccess { a.back() }.onFailure(a::report) }
    }, { delete = false })
    if(exportOptions){
        var includeVideo by remember(exportOptions,userPrefs.includeReferenceVideo,hasReferenceVideo){mutableStateOf(userPrefs.includeReferenceVideo&&hasReferenceVideo)}
        val estimate=remember(file,includeVideo){TrackExporter.estimate(file,includeVideo)}
        AlertDialog(onDismissRequest={exportOptions=false},title={Text(s(R.string.export_options))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            SettingRow(s(R.string.include_reference_video),includeVideo&&estimate.videoAvailable){if(estimate.videoAvailable)includeVideo=it}
            if(!estimate.videoAvailable)Text(s(R.string.reference_video_unavailable),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            InfoRow(s(R.string.tracking_data_size),formatBytes(estimate.trackingBytes));InfoRow(s(R.string.reference_video_size),formatBytes(if(estimate.videoAvailable)referenceVideo.length()else 0));InfoRow(s(R.string.estimated_export_size),formatBytes(estimate.totalBytes))
            Text(s(R.string.export_video_default_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},confirmButton={TextButton({a.scope.launch{a.preferences.flag("include_reference_video",includeVideo)};exportOptions=false;a.export(file,includeReferenceVideo=includeVideo)}){Text(s(R.string.export))}},dismissButton={TextButton({exportOptions=false}){Text(s(R.string.cancel))}})
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text(s(R.string.rename)) }, text = { Input(R.string.name, name) { name = it } },
        confirmButton = { TextButton({ a.guard {
            require(name.isNotBlank()); val m = meta ?: error("No metadata"); m.getAsJsonObject("shot").addProperty("name", name)
            val target = android.util.AtomicFile(File(file, "metadata.json")); val stream = target.startWrite()
            try { stream.write(GsonBuilder().setPrettyPrinting().create().toJson(m).toByteArray()); target.finishWrite(stream) } catch (t: Throwable) { target.failWrite(stream); throw t }
            revision++; rename = false
        } }) { Text(s(R.string.save)) } }, dismissButton = { TextButton({ rename = false }) { Text(s(R.string.cancel)) } })
}

private fun formatBytes(bytes:Long):String=when{
    bytes>=1024L*1024*1024->String.format(Locale.US,"%.2f GB",bytes/(1024.0*1024*1024))
    bytes>=1024L*1024->String.format(Locale.US,"%.2f MB",bytes/(1024.0*1024))
    bytes>=1024L->String.format(Locale.US,"%.1f KB",bytes/1024.0)
    else->"$bytes B"
}

@Composable private fun SummaryAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null, tint = RigTeal) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

