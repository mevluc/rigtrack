package com.rigtrack.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.rigtrack.R
import com.rigtrack.data.*

@Composable fun PresetsScreen(a: MainActivity) {
    val context = LocalContext.current; val cameras = remember { CameraCatalog.load(context) }
    var search by rememberSaveable { mutableStateOf("") }; var manufacturer by rememberSaveable { mutableIntStateOf(0) }
    var custom by rememberSaveable { mutableStateOf(false) }; var name by rememberSaveable { mutableStateOf("") }
    var width by rememberSaveable { mutableStateOf("") }; var height by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<CameraPreset, CameraMode>?>(null) }
    val makers = remember { cameras.map { it.manufacturer }.distinct() }
    Page {
        Input(R.string.search, search) { search = it }
        PresetSelector(s(R.string.camera), listOf("—") + makers, manufacturer) { manufacturer = it }
        SecondaryButton(s(R.string.custom_camera)) { custom = true; pending = null }
        if (custom) SectionCard(s(R.string.custom_camera)) {
            if (pending != null) Text(s(R.string.unverified_area), color = RigTeal)
            Input(R.string.camera, name) { name = it }; Input(R.string.sensor_width, width) { width = it }; Input(R.string.sensor_height, height) { height = it }
            PrimaryButton(s(R.string.save)) { a.guard {
                require(name.isNotBlank()); val mode = pending?.second
                val f = a.shot.film.copy(name = name, sensorWidthMm = width.number(1.0, 100.0), sensorHeightMm = height.number(1.0, 100.0), width = mode?.width ?: a.shot.film.width, height = mode?.height ?: a.shot.film.height)
                a.shot = a.shot.copy(film = f); a.confirmArea(f); a.save("films.json", a.repo.films().filter { it.name != name } + f); a.back()
            } }
        }
        a.repo.films().filter { manufacturer == 0 && it.name.contains(search, true) }.forEach { saved ->
            SectionCard(saved.name) {
                InfoRow(s(R.string.sensor), "${saved.sensorWidthMm} × ${saved.sensorHeightMm} mm")
                PrimaryButton(s(R.string.continue_action)) { val film = a.shot.film.copy(name = saved.name, sensorWidthMm = saved.sensorWidthMm, sensorHeightMm = saved.sensorHeightMm, width = saved.width, height = saved.height); a.shot = a.shot.copy(film = film); a.confirmArea(film); a.back() }
            }
        }
        cameras.filter { (manufacturer == 0 || it.manufacturer == makers[manufacturer - 1]) && "${it.manufacturer} ${it.model}".contains(search, true) }.forEach { camera ->
            var modeIndex by rememberSaveable(camera.id) { mutableIntStateOf(0) }
            SectionCard("${camera.manufacturer} ${camera.model}") {
                Text("${camera.sensorName} · ${camera.sensorWidthMm ?: "—"} × ${camera.sensorHeightMm ?: "—"} mm")
                PresetSelector(s(R.string.capture_mode), camera.modes.map { it.name }, modeIndex) { modeIndex = it
                    val mode = camera.modes[it]
                    if (mode.verified) { a.shot = a.shot.copy(film = a.shot.film.copy(name = "${camera.manufacturer} ${camera.model} · ${mode.name}", sensorWidthMm = mode.activeWidthMm!!, sensorHeightMm = mode.activeHeightMm!!, width = mode.width, height = mode.height)); a.confirmArea(a.shot.film); a.back() }
                }
                val mode = camera.modes[modeIndex]
                Text(if (LocalConfiguration.current.locales[0].language == "tr") camera.notesTr else camera.notesEn)
                if (mode.verified) { StatusChip(s(R.string.verified)); InfoRow(s(R.string.sensor), "${mode.activeWidthMm} × ${mode.activeHeightMm} mm") }
                else Text(s(R.string.unverified_area), color = RigRed)
                TextButton({ a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(camera.sourceUrl))) }) { Text("${s(R.string.source)} · ${camera.verifiedDate}") }
                PrimaryButton(s(R.string.continue_action)) {
                    if (mode.verified) {
                        a.shot = a.shot.copy(film = a.shot.film.copy(name = "${camera.manufacturer} ${camera.model} · ${mode.name}", sensorWidthMm = mode.activeWidthMm!!, sensorHeightMm = mode.activeHeightMm!!, width = mode.width, height = mode.height)); a.confirmArea(a.shot.film); a.back()
                    } else { custom = true; pending = camera to mode; name = "${camera.manufacturer} ${camera.model} · ${mode.name}"; width = ""; height = "" }
                }
            }
        }
    }
}

