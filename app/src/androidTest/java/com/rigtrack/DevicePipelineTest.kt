package com.rigtrack

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rigtrack.ui.MainActivity
import com.rigtrack.data.*
import com.rigtrack.export.TrackExporter
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class DevicePipelineTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun prepare() {
        runBlocking { UserPreferences(context).apply { flag("onboarding_done", true); flag("developer", true); language("en") } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun syntheticUiRecordingExport() {
        val sessions = File(context.filesDir, "sessions"); val existing = sessions.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        compose.runOnIdle { compose.activity.shot = compose.activity.shot.copy(synthetic = true) }
        compose.onNodeWithTag("new_shot").performClick()
        compose.onNodeWithTag("start_capture").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.activity.capture.controller?.latest != null }
        compose.onNodeWithTag("origin").performClick(); Thread.sleep(150)
        compose.onNodeWithTag("record").performClick(); Thread.sleep(1200)
        compose.onNodeWithTag("sync").performClick(); Thread.sleep(1900)
        screenshot("recording-test.png")
        runBlocking { UserPreferences(context).language("tr") }; screenshot("ui_tr_capture.png")
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(500); screenshot("ui_tr_capture_landscape.png")
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        runBlocking { UserPreferences(context).language("en") }; compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Save and leave capture?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(compose.activity.capture.controller?.writer)
        compose.onNodeWithTag("record").performClick()
        var completed: File? = null
        compose.waitUntil(10000) {
            completed = sessions.listFiles()?.filter { it.name !in existing && it.isDirectory && !it.name.endsWith(".partial") && File(it, "blender_camera.csv").exists() }?.maxByOrNull { it.lastModified() }
            completed != null
        }
        val file = completed ?: error("Recording did not finish")
        val meta = JsonParser.parseString(File(file, "metadata.json").readText()).asJsonObject
        assertTrue(meta.getAsJsonObject("shot")["synthetic"].asBoolean)
        assertTrue(File(file, "ar_pose.csv").readLines().size > 30)
        assertTrue(File(file, "events.csv").readText().contains("SYNC_001"))
        assertTrue(File(file, "events.csv").readText().contains("REFERENCE_VIDEO_FAILED"))
        assertFalse(meta["referenceVideoAvailable"].asBoolean)
        assertTrue(File(file, "blender_camera.csv").readLines().size > 50)
        val zip = File(context.filesDir, "device-smoke.vfxtrack"); TrackExporter.zip(file, zip.outputStream()); zip.copyTo(File(context.getExternalFilesDir(null), "device-smoke.vfxtrack"), overwrite = true)
        ZipFile(zip).use { assertNotNull(it.getEntry("film_camera_raw.csv")); assertNotNull(it.getEntry("markers.csv")) }
        screenshot("summary-test.png")
        compose.onNodeWithTag("export").performScrollTo().performClick()
        compose.onNodeWithText("Export options").assertIsDisplayed();compose.onNodeWithText("Include reference video").assertIsDisplayed()
        compose.onNodeWithText("Tracking data").assertIsDisplayed();compose.onNodeWithText("Estimated package").assertIsDisplayed()
        screenshot("export-options-no-video.png");compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("summary_new_shot").performScrollTo()
        screenshot("summary-actions.png")
        runBlocking { UserPreferences(context).language("tr") }; screenshot("ui_tr_summary.png")
    }
    @Test fun localizedScreensAndBackStack() {
        runBlocking { UserPreferences(context).flag("developer", false) }
        compose.runOnIdle { compose.activity.shot = compose.activity.shot.copy(synthetic = false) }
        for (language in listOf("en", "tr")) {
            runBlocking { UserPreferences(context).language(language) }
            compose.waitUntil(5000) { runBlocking { UserPreferences(context).state.first().language == language } }
            compose.waitForIdle(); assertEquals(language, runBlocking { UserPreferences(context).state.first().language })
            for (route in listOf("home", "new", "settings", "about", "presets", "generator", "guide", "rigs", "maps", "calibration", "recordings", "diagnostics")) {
                compose.runOnIdle { compose.activity.home(); if (route != "home") compose.activity.go(route) }
                if (route == "home") {
                    compose.waitUntil(5000) { compose.onAllNodesWithText(if (language == "en") "Post-production ready camera tracking." else "Post prodüksiyona hazır kamera takibi.").fetchSemanticsNodes().isNotEmpty() }
                    listOf("recordings", "rigs", "maps", "calibration", "generator", "guide", "settings", "diagnostics").forEach {
                        compose.onNodeWithTag(it).assertIsDisplayed()
                        compose.onNodeWithTag("${it}_description", useUnmergedTree = true).assertIsDisplayed()
                    }
                    compose.onNodeWithTag("new_shot_description",useUnmergedTree=true).assertIsDisplayed()
                    if(language=="en"){
                        val tags=listOf("recordings","rigs","maps","calibration","generator","guide","settings","diagnostics")
                        val bounds=tags.map{compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
                        assertTrue("Dashboard card heights differ: ${bounds.map{it.height}}",(bounds.maxOf{it.height}-bounds.minOf{it.height})<=2f)
                        val ctaHeight=compose.onNodeWithTag("new_shot").fetchSemanticsNode().boundsInRoot.height
                        val cardHeight=bounds.first().height
                        assertTrue("CTA/card height ratio is unbalanced: $ctaHeight/$cardHeight",ctaHeight/cardHeight in .95f..1.18f)
                        val rootBottom=compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
                        val bottomGap=rootBottom-bounds.maxOf{it.bottom}
                        assertTrue("Dashboard leaves an excessive bottom gap: $bottomGap",bottomGap<100*context.resources.displayMetrics.density)
                    }
                }
                if (route == "about") compose.onNodeWithText("Mevlüt Çetin").assertIsDisplayed()
                screenshot("ui_${language}_${route}.png")
                if (route != "home") {
                    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                    compose.runOnIdle { assertEquals(listOf("home"), compose.activity.routes.toList()) }
                }
            }
        }
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(500); screenshot("ui_tr_home_landscape.png")
        compose.runOnIdle { compose.activity.go("new") }; screenshot("ui_tr_new_landscape.png")
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    @Test fun presetDatabaseIsValidAndUnique() {
        val cameras = CameraCatalog.load(context)
        assertTrue(cameras.size >= 25); assertEquals(cameras.size, cameras.map { it.id }.toSet().size)
        assertTrue(cameras.any { it.manufacturer == "ARRI" }); assertTrue(cameras.any { it.manufacturer == "Panasonic" })
        val canon = cameras.first { it.model == "EOS C400" }
        assertEquals(36.0, canon.modes.first().activeWidthMm!!, 0.0)
        assertEquals(33.8, canon.modes.last().activeWidthMm!!, 0.0)
        assertTrue(cameras.first { it.model == "FX3" }.modes.none { it.verified })
    }
    @Test fun settingsLanguageSurvivesRecreationAndHomeBackNeedsTwoPresses() {
        compose.runOnIdle { compose.activity.go("settings") }
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Türkçe").performClick()
        compose.waitUntil(5000) { runBlocking { UserPreferences(context).state.first().language == "tr" } }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithText("Genel").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("new_shot").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed(); assertFalse(compose.activity.isFinishing) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed(); assertTrue(compose.activity.isFinishing) }
    }
    @Test fun backConfirmationSavesBeforeLeavingCapture() {
        compose.runOnIdle { compose.activity.shot = compose.activity.shot.copy(synthetic = true) }
        compose.onNodeWithTag("new_shot").performClick(); compose.onNodeWithTag("start_capture").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.activity.capture.controller?.latest != null }
        compose.onNodeWithTag("origin").performClick(); Thread.sleep(200)
        compose.onNodeWithTag("record").performClick(); Thread.sleep(1000)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        screenshot("recording-back-confirmation.png")
        compose.onNodeWithText("Stop, save and leave").performClick()
        compose.waitUntil(10000) { compose.activity.capture.controller == null }
        compose.runOnIdle { assertEquals("new", compose.activity.routes.last()); assertTrue(File(compose.activity.selected!!, "blender_camera.csv").exists()) }
    }
    @Test fun backgroundingCaptureFinalizesBeforeLifecycleResume() {
        val sessions = File(context.filesDir, "sessions")
        val existing = sessions.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        compose.runOnIdle { compose.activity.shot = compose.activity.shot.copy(synthetic = true) }
        compose.onNodeWithTag("new_shot").performClick()
        compose.onNodeWithTag("start_capture").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.activity.capture.controller?.latest != null }
        compose.onNodeWithTag("origin").performClick(); Thread.sleep(150)
        compose.onNodeWithTag("record").performClick(); Thread.sleep(800)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        var completed: File? = null
        val deadline = System.currentTimeMillis() + 10_000
        while (completed == null && System.currentTimeMillis() < deadline) {
            completed = sessions.listFiles()?.filter { it.name !in existing && it.isDirectory && !it.name.endsWith(".partial") }?.maxByOrNull { it.lastModified() }
            if (completed == null) Thread.sleep(100)
        }
        assertNotNull("Background stop did not finalize the shot", completed)
        val finalized = completed ?: error("Background stop did not finalize the shot")
        assertTrue(File(finalized, "events.csv").readText().contains("RECORDING_INTERRUPTED"))
        assertFalse(sessions.listFiles()?.any { it.name !in existing && it.name.endsWith(".partial") } == true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }
    @Test fun generatorPreviewAndOversizeValidation() {
        compose.runOnIdle { compose.activity.go("generator") }
        compose.onNodeWithTag("marker_mm").performScrollTo().performTextReplacement("300")
        compose.onNodeWithTag("pdf_preview").performScrollTo().performClick()
        compose.onNodeWithText("The selected pattern does not fit this paper with safe margins. Choose larger paper or a smaller pattern.").assertExists()
        compose.onNodeWithTag("marker_mm").performScrollTo().performTextReplacement("150")
        compose.onNodeWithTag("pdf_preview").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Preview").performScrollTo(); screenshot("ui_en_pdf_preview.png")
    }
    @Test fun duplicatedShotReplacesPreviouslySavedForm() {
        compose.runOnIdle { compose.activity.go("new") }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.back(); compose.activity.duplicateShot(compose.activity.shot.copy(film = compose.activity.shot.film.copy(focalLengthMm = 85.0))) }
        compose.onNodeWithText("85.0").assertExists()
    }
    @Test fun firstLaunchIsAThreePageProductIntroductionAndDoesNotRepeat() {
        runBlocking { UserPreferences(context).flag("onboarding_done", false); UserPreferences(context).language("en") }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithText("RigTrack").assertIsDisplayed()
        compose.waitForIdle()
        screenshot("onboarding_1.png")
        compose.onNodeWithTag("onboarding_next").performClick()
        compose.onNodeWithText("Camera motion for VFX").assertIsDisplayed()
        compose.waitForIdle()
        screenshot("onboarding_2.png")
        compose.onNodeWithTag("onboarding_next").performClick()
        compose.onNodeWithText("Ready for Blender").assertIsDisplayed()
        compose.waitForIdle()
        screenshot("onboarding_3.png")
        compose.onNodeWithText("Get Started").performClick()
        compose.onNodeWithTag("new_shot").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("new_shot").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_next").assertDoesNotExist()
    }
}


