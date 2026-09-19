package com.rigtrack

import com.rigtrack.data.CameraCatalog
import com.rigtrack.data.RecordingQuality
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CatalogAndLocalizationTest {
    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { name }
    @Test fun pairedLanguageResourcesAreComplete() {
        fun strings(name: String): Map<String, String> = resource(name).use { stream ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream).getElementsByTagName("string")
            (0 until nodes.length).associate { i -> nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent }
        }
        val en = strings("values/strings.xml"); val tr = strings("values-tr/strings.xml")
        assertEquals(en.keys, tr.keys); assertTrue(en.size > 200)
        assertTrue(en.values.none { it.isBlank() }); assertTrue(tr.values.none { it.isBlank() })
        assertEquals(18, en.keys.count { it.startsWith("guide_") && it.endsWith("_body") })
        assertNotEquals(en["record_exit_body"], tr["record_exit_body"])
    }
    @Test fun cameraFactsAreValidatedAndFocalLengthIsSeparate() {
        val json = resource("camera_presets.json").bufferedReader().use { it.readText() }
        val cameras = CameraCatalog.parse(json)
        assertEquals(37, cameras.size); assertEquals(60, cameras.sumOf { it.modes.size })
        assertFalse(json.contains("focalLength"))
        assertTrue(cameras.all { it.sourceUrl.startsWith("https://") && it.verifiedDate == "2026-09-07" })
        assertTrue(cameras.flatMap { it.modes }.filter { !it.verified }.all { it.activeWidthMm == null && it.activeHeightMm == null })
        val mini = cameras.first { it.model == "ALEXA Mini" }.modes.first { it.name == "UHD ProRes" }
        assertEquals(26.4, mini.activeWidthMm!!, 0.0); assertEquals(3840, mini.width)
    }
    @Test fun recordedQualityIncludesLossRatherThanJustCountingValidFrames() {
        val directory = kotlin.io.path.createTempDirectory("quality").toFile()
        try {
            File(directory, "film_camera_raw.csv").writeText("timestamp_ns,quality,valid\n1,80,true\n2,0,false\n3,70,true\n")
            assertEquals(50, RecordingQuality.average(directory))
        } finally { directory.deleteRecursively() }
    }
}
