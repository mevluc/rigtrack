package com.rigtrack.core.model

import com.rigtrack.core.math.*

data class Fps(val numerator: Int=25,val denominator: Int=1) {
    init { require(numerator>0 && denominator>0) }
    fun seconds(frameZero: Long)=frameZero.toDouble()*denominator/numerator
    fun nanos(frameZero: Long): Long = Math.multiplyExact(frameZero,1_000_000_000L*denominator)/numerator
    companion object { val labels=listOf("23.976","24","25","29.97","30","50","59.94","60"); fun parse(s:String)=when(s) { "23.976"->Fps(24000,1001); "29.97"->Fps(30000,1001); "59.94"->Fps(60000,1001); else->Fps(s.toInt(),1) } }
}
data class RigProfile(val name:String="No offset", val T_phoneCamera_filmCamera:Rigid=Rigid())
data class FilmSettings(val name:String="Sony FX3",val fps:Fps=Fps(),val sensorWidthMm:Double=35.6,val sensorHeightMm:Double=23.8,val focalLengthMm:Double=35.0,val lensName:String="",val focusDistanceM:Double=0.0,val width:Int=3840,val height:Int=2160)
enum class ReferenceVideoQuality {
    OFF, P720, P1080;
    val persisted: String get() = when (this) { OFF -> "off"; P720 -> "720p"; P1080 -> "1080p" }
    val targetWidth: Int get() = when (this) { OFF -> 0; P720 -> 1280; P1080 -> 1920 }
    val targetHeight: Int get() = when (this) { OFF -> 0; P720 -> 720; P1080 -> 1080 }
    companion object {
        fun parse(value: String?) = when (value?.lowercase()) { "off" -> OFF; "1080p" -> P1080; else -> P720 }
    }
}
data class ShotSettings(val name:String="shot_001",val film:FilmSettings=FilmSettings(),val rig:RigProfile=RigProfile(),val smoothing:String="Low",val gapMaxMs:Int=100,val synthetic:Boolean=false,val motion:String="Circle",val referenceVideoQuality:ReferenceVideoQuality?=ReferenceVideoQuality.P720)
data class Intrinsics(val fx:Double,val fy:Double,val cx:Double,val cy:Double,val width:Int,val height:Int)
data class ArPoseSample(val timestampNs:Long,val receivedNs:Long,val androidCameraTimestampNs:Long,val worldCamera:Rigid,val worldAnchor:Rigid?,val state:String,val failure:String,val intrinsics:Intrinsics,val anchorTracking:Boolean=true,val qualityScore:Double=100.0) {
    val relative get()=worldAnchor?.inverse()?.times(worldCamera)
    val valid get()=state=="TRACKING" && worldAnchor!=null && anchorTracking
}
data class ImuSample(val timestampNs:Long,val receivedNs:Long,val type:Int,val values:List<Float>,val accuracy:Int)
data class TimedPose(val timestampNs:Long,val pose:Rigid,val valid:Boolean=true,val quality:Double=100.0)
data class MarkerDictionarySpec(val id:Int,val label:String)
object MarkerDictionaries {
    const val APRILTAG_36H11 = 20
    const val DEFAULT = APRILTAG_36H11
    val all = listOf(
        MarkerDictionarySpec(APRILTAG_36H11,"AprilTag 36h11"), MarkerDictionarySpec(0,"ArUco 4×4 / 50"),
        MarkerDictionarySpec(1,"ArUco 4×4 / 100"), MarkerDictionarySpec(4,"ArUco 5×5 / 50"),
        MarkerDictionarySpec(5,"ArUco 5×5 / 100"), MarkerDictionarySpec(8,"ArUco 6×6 / 50"),
        MarkerDictionarySpec(9,"ArUco 6×6 / 100"), MarkerDictionarySpec(12,"ArUco 7×7 / 50"))
    fun label(id:Int)=all.firstOrNull{it.id==id}?.label?:"Dictionary $id"
}
data class MarkerReference(val id:Int,val sizeM:Double,val T_anchor_marker:Rigid,val observations:Int,
    val translationStddevM:Double?=null,val rotationStddevDeg:Double?=null,val meanReprojectionError:Double?=null,val confidence:Double?=null)
data class MarkerMap(val name:String="Stage",val dictionary:Int=MarkerDictionaries.DEFAULT,val references:List<MarkerReference> = emptyList(),val originNote:String="Valid only in this anchor epoch; relocalize from mapped markers after restart",val markerSizesM:Map<Int,Double> = emptyMap())
data class CalibrationProfile(val name:String,val key:String,val intrinsics:Intrinsics,val distortion:List<Double>,val rms:Double,val samples:Int,val created:String)
data class MarkerObservation(val timestampNs:Long,val imageTimestampNs:Long,val receivedNs:Long,val id:Int,val sizeM:Double,val corners:List<Double>,val rvec:V3,val tvec:V3,val T_camera_marker:Rigid,val reprojectionError:Double,val pixelArea:Double,val viewAngle:Double,val confidence:Double,val intrinsics:Intrinsics,val calibrated:Boolean)
data class DetectorSettings(val dictionary:Int=MarkerDictionaries.DEFAULT,val markerSizeMm:Double=150.0,val fps:Int=15,val minPixelArea:Double=900.0,val maxError:Double=2.0,val correctionStrength:Double=0.12,val minConfidence:Double=0.40,val preferredFps:Int=0,val sensorPeriodUs:Int=0,val debug:Boolean=false,val boardX:Int=5,val boardY:Int=7,val squareMm:Double=40.0,val boardMarkerMm:Double=30.0,
    val translationDeadZoneMm:Double=3.0,val rotationDeadZoneDeg:Double=0.15,val relockConfirmationFrames:Int=4,
    val relockTranslationBlendMs:Int=300,val relockRotationBlendMs:Int=400,val innovationTranslationMm:Double=50.0,
    val innovationRotationDeg:Double=3.0,val movedTranslationMm:Double=25.0,val movedRotationDeg:Double=1.5,val movedConfirmationFrames:Int=5)

enum class MarkerRejectionReason { LOW_CONFIDENCE,HIGH_REPROJECTION_ERROR,OUTLIER_TRANSLATION,OUTLIER_ROTATION,MARKER_MOVED,MAP_QUALITY_LOW,ARCORE_NOT_TRACKING,NOT_MAPPED,AWAITING_CONFIRMATION,NONE }
data class MarkerUseDecision(val mapped:Boolean,val valid:Boolean,val usedForCorrection:Boolean,val rejectionReason:MarkerRejectionReason,
    val innovationTranslationM:Double?=null,val innovationRotationDeg:Double?=null)
