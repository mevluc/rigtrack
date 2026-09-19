package com.rigtrack.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.rigtrack.R
import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import com.rigtrack.data.Preferences
import com.rigtrack.recording.ReferenceVideoRecorder
import java.text.SimpleDateFormat
import java.util.*

@Composable fun HomeScreen(a: MainActivity) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val availableHeight = maxHeight
    val wide = maxWidth > maxHeight || maxWidth > 600.dp
    val fallbackScroll = LocalConfiguration.current.fontScale > 1.25f || (!wide && maxHeight < 620.dp)
    val roomy = maxHeight >= 960.dp
    val compactWide = wide && maxHeight < 600.dp
    val gap = if (roomy) 12.dp else 8.dp
    val cards = listOf(
        Triple("recordings", R.string.recordings, R.string.home_recordings_description), Triple("rigs", R.string.rig_profiles, R.string.home_rig_profiles_description),
        Triple("maps", R.string.marker_maps, R.string.home_marker_maps_description), Triple("calibration", R.string.calibration, R.string.home_camera_calibration_description),
        Triple("generator", R.string.generator, R.string.home_marker_generator_description), Triple("guide", R.string.guide, R.string.home_guide_description),
        Triple("settings", R.string.settings, R.string.home_settings_description), Triple("diagnostics", R.string.diagnostics, R.string.home_diagnostics_description))
    val columns = if (wide) 4 else 2
    val contentModifier = Modifier.fillMaxSize()
        .then(if (fallbackScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        .padding(horizontal = 16.dp, vertical = if (roomy) 16.dp else 12.dp)
    Column(contentModifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Text("RIGTRACK", style = MaterialTheme.typography.labelLarge, color = RigTeal)
        Text(s(R.string.home_intro), style = if (availableHeight < 700.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
        Text(s(R.string.home_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HomeActions(a, cards, columns, balanced = !fallbackScroll, roomy = roomy, compactWide = compactWide)
    }
}

@Composable private fun ColumnScope.HomeActions(a: MainActivity, cards: List<Triple<String, Int, Int>>, columns: Int, balanced: Boolean, roomy: Boolean, compactWide:Boolean) {
    val rows = cards.chunked(columns)
    Column(Modifier.fillMaxWidth().then(if (balanced) Modifier.weight(1f) else Modifier), verticalArrangement = Arrangement.spacedBy(if (roomy) 10.dp else 8.dp)) {
        Card(onClick = { a.go("new") }, colors = CardDefaults.cardColors(containerColor = RigTeal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().then(if(balanced)Modifier.weight(1.1f)else Modifier.heightIn(min=96.dp)).testTag("new_shot")) {
            Row(Modifier.fillMaxSize().padding(horizontal=if(roomy)18.dp else 14.dp,vertical=if(compactWide)7.dp else 10.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam,null,Modifier.size(if(roomy)28.dp else 24.dp),tint=RigBackground)
                Column(verticalArrangement=Arrangement.spacedBy(if(compactWide)2.dp else 4.dp)){
                    Text(s(R.string.new_shot),color=RigBackground,style=MaterialTheme.typography.titleMedium,maxLines=1)
                    Text(s(R.string.new_shot_hint),color=RigBackground.copy(alpha=.78f),style=MaterialTheme.typography.bodySmall,maxLines=if(compactWide)1 else 2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.testTag("new_shot_description"))
                }
            }
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().then(if (balanced) Modifier.weight(1f) else Modifier.heightIn(min = 96.dp)), horizontalArrangement = Arrangement.spacedBy(if (roomy) 10.dp else 8.dp)) {
                row.forEach { (route, title, description) ->
                    Card(onClick = { a.go(route) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag(route)) {
                        val icon=when (route) { "recordings" -> Icons.Default.FolderOpen; "rigs" -> Icons.Default.Straighten; "maps" -> Icons.Default.Map; "calibration" -> Icons.Default.CenterFocusStrong; "generator" -> Icons.Default.QrCode2; "guide" -> Icons.Default.MenuBook; "settings" -> Icons.Default.Settings; else -> Icons.Default.Insights }
                        if(compactWide)Row(Modifier.fillMaxSize().padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Icon(icon,null,Modifier.size(19.dp),tint=RigTeal)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                                Text(s(title),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(s(description),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.testTag("${route}_description"))
                            }
                        }else Column(Modifier.fillMaxSize().padding(horizontal = if (roomy) 16.dp else 12.dp, vertical = if(roomy)14.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(if(roomy)7.dp else 5.dp)) {
                            Icon(icon, null, Modifier.size(if(roomy)26.dp else 22.dp), tint = RigTeal)
                            Text(s(title), style = MaterialTheme.typography.titleSmall, maxLines = if(compactWide)1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(s(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if(compactWide)1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("${route}_description"))
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal fun String.number(min: Double, max: Double): Double = replace(',', '.').toDouble().also { require(it.isFinite() && it in min..max) }

@Composable fun NewShotScreen(a: MainActivity, prefs: Preferences) {
    val film = a.shot.film
    var name by rememberSaveable { mutableStateOf(a.shot.name) }
    var focal by rememberSaveable { mutableStateOf(film.focalLengthMm.toString()) }
    var width by rememberSaveable(film.sensorWidthMm) { mutableStateOf(film.sensorWidthMm.toString()) }
    var height by rememberSaveable(film.sensorHeightMm) { mutableStateOf(film.sensorHeightMm.toString()) }
    var rw by rememberSaveable(film.width) { mutableStateOf(film.width.toString()) }
    var rh by rememberSaveable(film.height) { mutableStateOf(film.height.toString()) }
    var focus by rememberSaveable { mutableStateOf(film.focusDistanceM.toString()) }
    var lens by rememberSaveable { mutableStateOf(film.lensName) }
    var fps by rememberSaveable { mutableIntStateOf(Fps.labels.indexOfFirst { Fps.parse(it) == film.fps }.coerceAtLeast(0)) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var synthetic by rememberSaveable { mutableStateOf(a.shot.synthetic && prefs.developer) }
    var motion by rememberSaveable { mutableIntStateOf(listOf("Circle", "Forward/back", "Pan", "Orbit", "Test trajectory").indexOf(a.shot.motion).coerceAtLeast(0)) }
    val rigs = remember { a.repo.rigs() }; val maps = remember { listOf(MarkerMap()) + a.repo.maps() }
    var rig by rememberSaveable { mutableIntStateOf(rigs.indexOfFirst { it.name == a.shot.rig.name }.coerceAtLeast(0)) }
    var map by rememberSaveable { mutableIntStateOf(maps.indexOfFirst { it.name == a.selectedMap.name }.coerceAtLeast(0)) }
    val smoothing = listOf("Off", "Low", "Medium", "High")
    var smooth by rememberSaveable { mutableIntStateOf(smoothing.indexOf(a.shot.smoothing).coerceAtLeast(0)) }
    val gapLimits=listOf(50,100,200);var gapLimit by rememberSaveable { mutableIntStateOf(gapLimits.indexOf(a.shot.gapMaxMs).coerceAtLeast(0)) }
    val referenceQualities = listOf(ReferenceVideoQuality.OFF, ReferenceVideoQuality.P720, ReferenceVideoQuality.P1080)
    var referenceQuality by rememberSaveable { mutableIntStateOf(referenceQualities.indexOf(a.shot.referenceVideoQuality ?: ReferenceVideoQuality.P720).coerceAtLeast(0)) }
    var validation by remember { mutableStateOf(false) }
    var areaChecked by rememberSaveable { mutableStateOf(a.areaConfirmed(film)) }
    LaunchedEffect(film.name) { width = film.sensorWidthMm.toString(); height = film.sensorHeightMm.toString(); rw = film.width.toString(); rh = film.height.toString(); areaChecked = a.areaConfirmed(film) }
    Page {
        SectionCard(s(R.string.new_shot)) {
            Input(R.string.shot_name, name) { name = it }
            SecondaryButton("${s(R.string.camera_preset)} · ${film.name}", Modifier.fillMaxWidth()) { a.shot = a.shot.copy(name = name); a.go("presets") }
            PresetSelector(s(R.string.fps), Fps.labels, fps) { fps = it }
            Input(R.string.focal, focal) { focal = it }
            PresetSelector(s(R.string.rig_profiles), rigs.map { if (it.name == "No offset") s(R.string.no_offset) else it.name }, rig) { rig = it }
            PresetSelector(s(R.string.marker_maps), maps.mapIndexed { i, m -> if (i == 0) s(R.string.no_map) else "${m.name} · ${MarkerDictionaries.label(m.dictionary)}" }, map) {
                map = it;if(it>0)a.detector=a.detector.copy(dictionary=maps[it].dictionary)
            }
            if(map>0)Text("${s(R.string.dictionary)} · ${MarkerDictionaries.label(maps[map].dictionary)}",style=MaterialTheme.typography.bodySmall,color=RigTeal)
            PresetSelector(s(R.string.reference_video), listOf(s(R.string.reference_video_off), s(R.string.reference_video_720p), s(R.string.reference_video_1080p)), referenceQuality) { referenceQuality = it }
            Text(s(R.string.reference_video_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton({ advanced = !advanced }) { Text(s(R.string.advanced)); Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
        if (!areaChecked && !synthetic) Text(s(R.string.unverified_area), color = RigRed)
        if (advanced) SectionCard(s(R.string.advanced)) {
            Input(R.string.sensor_width, width) { width = it; areaChecked = false }; Input(R.string.sensor_height, height) { height = it; areaChecked = false }
            Input(R.string.resolution_width, rw) { rw = it; areaChecked = false }; Input(R.string.resolution_height, rh) { rh = it; areaChecked = false }
            SettingRow(s(R.string.area_confirm), areaChecked) { areaChecked = it }
            Input(R.string.lens, lens) { lens = it }; Input(R.string.focus, focus) { focus = it }
            PresetSelector(s(R.string.smoothing), listOf(s(R.string.off), s(R.string.low), s(R.string.medium), s(R.string.high)), smooth) { smooth = it }
            val translationZones=listOf(1.0,3.0,5.0,10.0)
            PresetSelector(s(R.string.translation_dead_zone),translationZones.map{"${it.toInt()} mm"},translationZones.indexOf(a.detector.translationDeadZoneMm).coerceAtLeast(0)){a.detector=a.detector.copy(translationDeadZoneMm=translationZones[it])}
            val rotationZones=listOf(.05,.15,.3,.5)
            PresetSelector(s(R.string.rotation_dead_zone),rotationZones.map{"$it°"},rotationZones.indexOf(a.detector.rotationDeadZoneDeg).coerceAtLeast(0)){a.detector=a.detector.copy(rotationDeadZoneDeg=rotationZones[it])}
            val confirmations=listOf(3,4,5)
            PresetSelector(s(R.string.relock_confirmation),confirmations.map{"$it"},confirmations.indexOf(a.detector.relockConfirmationFrames).coerceAtLeast(0)){a.detector=a.detector.copy(relockConfirmationFrames=confirmations[it])}
            val blends=listOf(250,300,500)
            PresetSelector(s(R.string.translation_blend),blends.map{"$it ms"},blends.indexOf(a.detector.relockTranslationBlendMs).coerceAtLeast(0)){a.detector=a.detector.copy(relockTranslationBlendMs=blends[it])}
            PresetSelector(s(R.string.rotation_blend),blends.map{"$it ms"},blends.indexOf(a.detector.relockRotationBlendMs).coerceAtLeast(0)){a.detector=a.detector.copy(relockRotationBlendMs=blends[it])}
            val confidences=listOf(.4,.6,.8)
            PresetSelector(s(R.string.minimum_confidence),confidences.map{"${(it*100).toInt()}%"},confidences.indexOf(a.detector.minConfidence).coerceAtLeast(0)){a.detector=a.detector.copy(minConfidence=confidences[it])}
            PresetSelector(s(R.string.gap_repair_limit),gapLimits.map{"$it ms"},gapLimit){gapLimit=it}
            val rates = listOf(5, 10, 15, 20, 30)
            PresetSelector(s(R.string.marker_rate), rates.map { "$it" }, rates.indexOf(a.detector.fps).coerceAtLeast(0)) { a.detector = a.detector.copy(fps = rates[it]) }
            PresetSelector(s(R.string.ar_fps), listOf(s(R.string.auto), "30", "60"), listOf(0, 30, 60).indexOf(a.detector.preferredFps).coerceAtLeast(0)) { a.detector = a.detector.copy(preferredFps = listOf(0, 30, 60)[it]) }
        }
        if (prefs.developer) SectionCard(s(R.string.developer)) {
            SettingRow(s(R.string.synthetic), synthetic) { synthetic = it }
            if (synthetic) { Text(s(R.string.synthetic_hint)); PresetSelector(s(R.string.motion), listOf(s(R.string.motion_circle), s(R.string.motion_forward), s(R.string.motion_pan), s(R.string.motion_orbit), s(R.string.motion_test)), motion) { motion = it } }
        }
        if (validation) Text(s(R.string.invalid_value), color = RigRed)
        val selectedReferenceQuality = referenceQualities[referenceQuality]
        if (a.repo.sessions.usableSpace < 1024L * 1024 * 1024) Text(s(R.string.storage_low), color = RigRed)
        if (selectedReferenceQuality != ReferenceVideoQuality.OFF && a.repo.sessions.usableSpace - 250L * 1024 * 1024 < ReferenceVideoRecorder.estimatedBytes(selectedReferenceQuality)) Text(s(R.string.reference_video_storage_warning), color = RigRed)
        PrimaryButton(s(R.string.start), Modifier.fillMaxWidth().testTag("start_capture")) {
            runCatching {
                require(synthetic || areaChecked)
                val f = film.copy(fps = Fps.parse(Fps.labels[fps]), focalLengthMm = focal.number(1.0, 2000.0), sensorWidthMm = width.number(1.0, 100.0), sensorHeightMm = height.number(0.0, 100.0), width = rw.number(1.0, 32000.0).toInt(), height = rh.number(1.0, 32000.0).toInt(), focusDistanceM = focus.number(0.0, 10000.0), lensName = lens)
                if (areaChecked) a.confirmArea(f)
                a.shot = ShotSettings(name.ifBlank { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }, f, rigs[rig], smoothing[smooth], gapLimits[gapLimit], synthetic && prefs.developer, listOf("Circle", "Forward/back", "Pan", "Orbit", "Test trajectory")[motion], selectedReferenceQuality)
                a.selectedMap = maps[map]; if (a.selectedMap.references.isNotEmpty()) a.detector = a.detector.copy(dictionary = a.selectedMap.dictionary)
                validation = false; a.begin()
            }.onFailure { validation = true }
        }
    }
}

@Composable fun RigDiagram() {
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val phone = Offset(size.width * .18f, size.height * .15f); val film = Offset(size.width * .55f, size.height * .4f)
        drawRoundRect(RigTeal, phone, Size(34.dp.toPx(), 65.dp.toPx()), CornerRadius(7.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawRoundRect(Color(0xFF9DBDBD), film, Size(75.dp.toPx(), 45.dp.toPx()), CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawCircle(RigTeal, 9.dp.toPx(), phone + Offset(17.dp.toPx(), 15.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawLine(RigTeal, phone + Offset(40.dp.toPx(), 45.dp.toPx()), film - Offset(8.dp.toPx(), 0f), 2.dp.toPx())
        drawLine(RigTeal, film - Offset(8.dp.toPx(), 0f), film - Offset(18.dp.toPx(), 7.dp.toPx()), 2.dp.toPx())
    }
}

@Composable fun RigsScreen(a: MainActivity) {
    var editing by remember { mutableStateOf<RigProfile?>(null) }; var revision by remember { mutableIntStateOf(0) }
    val rigs = remember(revision) { a.repo.rigs() }
    val initial = editing
    if (initial == null) Page {
        Text(s(R.string.rig_explanation)); RigDiagram()
        rigs.forEach { r -> SectionCard(if (r.name == "No offset") s(R.string.no_offset) else r.name) { InfoRow(s(R.string.horizontal), "%.1f mm".format(Locale.US, r.T_phoneCamera_filmCamera.t.x * 1000)); InfoRow(s(R.string.forward), "%.1f mm".format(Locale.US, -r.T_phoneCamera_filmCamera.t.z * 1000)); SecondaryButton(s(R.string.details)) { editing = r } } }
        PrimaryButton(s(R.string.add_profile)) { editing = RigProfile("") }
    } else {
        var name by remember(initial) { mutableStateOf(initial.name) }
        val t = initial.T_phoneCamera_filmCamera.t; val m = initial.T_phoneCamera_filmCamera.matrix()
        val values = remember(initial) { mutableStateListOf("${t.x * 1000}", "${t.y * 1000}", "${-t.z * 1000}", "${Math.toDegrees(kotlin.math.atan2(m[9], m[10]))}", "${Math.toDegrees(kotlin.math.asin((-m[8]).coerceIn(-1.0, 1.0)))}", "${Math.toDegrees(kotlin.math.atan2(m[4], m[0]))}") }
        var invalid by remember { mutableStateOf(false) }
        DisposableEffect(initial) { a.nestedBack = { editing = null }; onDispose { a.nestedBack = null } }
        Page {
            RigDiagram(); Text(s(R.string.rig_explanation)); Input(R.string.name, name) { name = it }
            listOf(R.string.horizontal, R.string.vertical, R.string.forward, R.string.pitch, R.string.yaw, R.string.roll).forEachIndexed { i, label -> Input(label, values[i]) { values[i] = it } }
            Text(s(R.string.rig_axes), style = MaterialTheme.typography.bodySmall)
            if (invalid) Text(s(R.string.invalid_value), color = RigRed)
            PrimaryButton(s(R.string.save)) {
                runCatching {
                    require(name.isNotBlank()); val n = values.mapIndexed { i, value -> value.number(if (i < 3) -5000.0 else -360.0, if (i < 3) 5000.0 else 360.0) }
                    val r = RigProfile(name, Rigid(V3(n[0] / 1000, n[1] / 1000, -n[2] / 1000), Q.eulerXYZ(Math.toRadians(n[3]), Math.toRadians(n[4]), Math.toRadians(n[5]))))
                    a.repo.save("rigs.json", rigs.filter { it.name != initial.name && it.name != name } + r); revision++; editing = null
                }.onFailure { invalid = true }
            }
            SecondaryButton(s(R.string.cancel)) { editing = null }
        }
    }
}

@Composable fun MapsScreen(a: MainActivity) {
    var creating by rememberSaveable { mutableStateOf(false) }; var name by rememberSaveable { mutableStateOf("") }; var sizes by rememberSaveable { mutableStateOf("") }
    val dictionaries=MarkerDictionaries.all
    var dictionary by rememberSaveable { mutableIntStateOf(dictionaries.indexOfFirst{it.id==a.detector.dictionary}.coerceAtLeast(0)) }
    Page {
        Text(s(R.string.map_instruction))
        val maps = remember { a.repo.maps() }
        if (maps.isEmpty()) EmptyState(s(R.string.empty_maps), s(R.string.empty_maps_hint))
        maps.forEach { map -> SectionCard(map.name) {
            InfoRow(s(R.string.dictionary), MarkerDictionaries.label(map.dictionary))
            InfoRow(s(R.string.markers), "${map.references.size}")
            map.references.forEach { Text("ID ${it.id} · ${it.sizeM * 1000} mm · ${it.observations}") }
            SecondaryButton(s(R.string.start)) { a.selectedMap = map; a.detector = a.detector.copy(dictionary = map.dictionary); a.shot = a.shot.copy(synthetic = false); a.begin("map") }
        } }
        if (!creating) PrimaryButton(s(R.string.new_map)) { creating = true }
        else SectionCard(s(R.string.new_map)) {
            Input(R.string.map_name, name) { name = it }; Input(R.string.map_sizes, sizes) { sizes = it }
            PresetSelector(s(R.string.dictionary),dictionaries.map{it.label},dictionary){dictionary=it}
            PrimaryButton(s(R.string.start)) { a.guard {
                val overrides = parseMarkerSizes(sizes)
                a.detector=a.detector.copy(dictionary=dictionaries[dictionary].id)
                a.selectedMap = MarkerMap(name.ifBlank { "${System.currentTimeMillis()}" }, dictionaries[dictionary].id, markerSizesM = overrides)
                a.shot = a.shot.copy(synthetic = false); a.begin("map")
            } }
        }
    }
}

fun parseMarkerSizes(value:String):Map<Int,Double> = value.split(',').filter{it.isNotBlank()}.associate{entry->
    val pair=entry.trim().split(':');require(pair.size==2)
    val id=pair[0].trim().toInt();require(id>=0)
    val millimeters=pair[1].trim().toDouble();require(millimeters in 1.0..2000.0)
    id to millimeters/1000.0
}

@Composable fun CalibrationSetupScreen(a: MainActivity) = Page {
    Text(s(R.string.calibration_instruction)); Text(s(R.string.board_match), color = RigTeal)
    BoardSettings(a)
    PrimaryButton(s(R.string.start), Modifier.fillMaxWidth()) { a.shot = a.shot.copy(synthetic = false); a.begin("calibration") }
    SecondaryButton(s(R.string.generator), Modifier.fillMaxWidth()) { a.go("generator") }
}

@Composable fun BoardSettings(a: MainActivity) {
    val d = a.detector
    val values = remember { mutableStateListOf("${d.boardX}", "${d.boardY}", "${d.squareMm}", "${d.boardMarkerMm}") }
    SectionCard(s(R.string.charuco_tab)) {
        val dictionaries = com.rigtrack.printing.MarkerPdf.dictionaries
        PresetSelector(s(R.string.dictionary), dictionaries.map { it.second }, dictionaries.indexOfFirst { it.first == a.detector.dictionary }.coerceAtLeast(0)) { a.detector = a.detector.copy(dictionary = dictionaries[it].first) }
        listOf(R.string.board_columns, R.string.board_rows, R.string.square_size, R.string.board_marker_size).forEachIndexed { i, label -> Input(label, values[i]) { values[i] = it } }
        PrimaryButton(s(R.string.save)) { a.guard {
            val x = values[0].number(3.0, 20.0).toInt(); val y = values[1].number(3.0, 20.0).toInt(); val sq = values[2].number(1.0, 1000.0); val marker = values[3].number(1.0, 1000.0); require(marker < sq)
            a.detector = a.detector.copy(boardX = x, boardY = y, squareMm = sq, boardMarkerMm = marker); a.save("settings.json", a.detector); a.notice = R.string.saved
        } }
    }
}
