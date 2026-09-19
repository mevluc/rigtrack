package com.rigtrack.data

import android.content.Context
import com.google.gson.Gson

data class CameraMode(val id: String, val name: String, val width: Int, val height: Int, val aspectRatio: String,
    val activeWidthMm: Double?, val activeHeightMm: Double?, val verified: Boolean)
data class CameraPreset(val id: String, val manufacturer: String, val model: String, val sensorName: String,
    val sensorWidthMm: Double?, val sensorHeightMm: Double?, val sensorType: String,
    val sourceName: String, val sourceUrl: String, val verifiedDate: String, val verified: Boolean,
    val notesEn: String, val notesTr: String, val modes: List<CameraMode>)
object CameraCatalog {
    fun parse(json: String): List<CameraPreset> = Gson().fromJson(json, Array<CameraPreset>::class.java).toList().also { cameras ->
        require(cameras.map { it.id }.distinct().size == cameras.size)
        cameras.forEach { camera ->
            require(camera.id.isNotBlank() && camera.manufacturer.isNotBlank() && camera.model.isNotBlank())
            require(camera.sourceUrl.startsWith("https://") && camera.verifiedDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            require(camera.modes.map { it.id }.distinct().size == camera.modes.size)
            camera.modes.forEach { mode ->
                require(mode.width > 0 && mode.height > 0)
                if (mode.verified) require(mode.activeWidthMm != null && mode.activeWidthMm.isFinite() && mode.activeWidthMm > 0 && mode.activeHeightMm != null && mode.activeHeightMm.isFinite() && mode.activeHeightMm > 0)
            }
        }
    }
    fun load(context: Context) = context.assets.open("camera_presets.json").bufferedReader().use { parse(it.readText()) }
}
