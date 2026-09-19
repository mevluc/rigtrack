package com.rigtrack.export

import com.google.gson.*
import com.rigtrack.core.model.*
import com.rigtrack.core.math.*
import java.io.*
import java.util.zip.*

object TrackExporter {
    private val gson=GsonBuilder().serializeNulls().setPrettyPrinting().create()
    data class ExportEstimate(val trackingBytes:Long,val videoBytes:Long,val totalBytes:Long,val videoAvailable:Boolean)
    fun process(dir:File) {
        val meta=JsonParser.parseString(File(dir,"metadata.json").readText()).asJsonObject
        require(meta["format_version"].asInt==1){"Unsupported vfxtrack format"}
        val settings=gson.fromJson(meta["shot"],ShotSettings::class.java)
        val start=meta["record_start_monotonic_ns"].asLong
        val stop=meta["record_stop_monotonic_ns"]?.asLong?:start
        val offset=meta["ar_to_elapsed_estimated_offset_ns"]?.asLong?:0
        var frames=0L;var invalid=0L
        for(fused in listOf(false,true)) {
            val input=File(dir,if(fused)"film_camera_fused.csv" else "film_camera_raw.csv")
            val output=File(dir,if(fused)"blender_camera.csv" else "blender_camera_raw.csv")
            output.bufferedWriter().use {w->
                w.appendLine("frame,time_s,tx,ty,tz,qx,qy,qz,qw,focal_length_mm,sensor_width_mm,quality,valid,interpolated_gap")
                input.bufferedReader().use { r ->
                    var source=r.lineSequence().drop(1).filter{it.isNotBlank()}.map {Csv.parse(it)}.map {v->TimedPose(v[0].toLong()+offset,if(v[2].isNotEmpty())Csv.rigid(v,2)else Rigid(),v[10].toBoolean(),v[9].toDouble())}
                    for(f in Resampler.frames(source,settings.film.fps,start,stop,settings.gapMaxMs*1_000_000L)) {
                        val pose=f.pose?.let(CoordinateConversion::toBlenderCamera)
                        w.appendLine(Csv.row(*(listOf(f.index,f.timeS)+(pose?.let(Csv::pose)?:List<Any?>(7){null})+listOf(settings.film.focalLengthMm,settings.film.sensorWidthMm,f.quality,pose!=null,f.interpolatedGap)).toTypedArray()))
                        if(fused){frames++;if(pose==null)invalid++}
                    }
                }
            }
        }
        val gapNs=settings.gapMaxMs*1_000_000L
        val referenceRefinement=TrackRefiner.write(File(dir,"phone_camera_fused.csv"),File(dir,"phone_camera_refined.csv"),settings.smoothing,gapNs)
        deriveRigTrack(File(dir,"phone_camera_refined.csv"),File(dir,"film_camera_refined.csv"),settings.rig.T_phoneCamera_filmCamera)
        val filmRefinement=TrackRefiner.evaluate(TrackRefiner.read(File(dir,"film_camera_fused.csv")),TrackRefiner.readSamples(File(dir,"film_camera_refined.csv")),referenceRefinement)
        writeFilmTrack(File(dir,"film_camera_refined.csv"),File(dir,"blender_camera_refined.csv"),settings,start,stop,offset)
        writeReferenceTrack(File(dir,"phone_camera_fused.csv"),File(dir,"blender_reference_camera.csv"),settings,start,stop,offset,false)
        writeReferenceTrack(File(dir,"phone_camera_refined.csv"),File(dir,"blender_reference_camera_refined.csv"),settings,start,stop,offset,false)
        writeReferenceTrack(File(dir,"ar_pose.csv"),File(dir,"blender_reference_camera_raw.csv"),settings,start,stop,offset,true)
        val rawMetrics=TrackRefiner.refine(TrackRefiner.read(File(dir,"film_camera_raw.csv")),"Off",gapNs).metrics
        val refinementConfig=TrackRefiner.Config.from(settings.smoothing)
        meta.add("refinement",gson.toJsonTree(mapOf(
            "enabled" to true,"smoothingEnabled" to !settings.smoothing.equals("Off",true),"preset" to settings.smoothing,"gapMaxMs" to settings.gapMaxMs,
            "algorithm" to "robust isolated-outlier rejection; bounded SLERP gap repair; centered symmetric quaternion-aware smoothing",
            "translationSettings" to mapOf("kernelRadius" to refinementConfig.radius,"strength" to refinementConfig.strength,"outlierFloorM" to refinementConfig.translationFloorM,"outlierSigma" to refinementConfig.outlierSigma),
            "rotationSettings" to mapOf("kernelRadius" to refinementConfig.radius,"strength" to refinementConfig.strength,"outlierFloorDeg" to refinementConfig.rotationFloorDeg,"quaternionHemisphereContinuity" to true),
            "rawSource" to "film_camera_raw.csv","fusedSource" to "film_camera_fused.csv","refinedSource" to "film_camera_refined.csv",
            "blenderRefinedSource" to "blender_camera_refined.csv","filmMetrics" to filmRefinement,"referenceMetrics" to referenceRefinement)))
        addCameraMapping(meta,dir,settings)
        File(dir,"metadata.json").writeText(gson.toJson(meta))
        for(name in listOf("calibration.json","marker_map.json"))if(!File(dir,name).exists())File(dir,name).writeText("null")
        val diagnostics=File(dir,"diagnostics.json")
        val diag=if(diagnostics.exists())JsonParser.parseString(diagnostics.readText()).asJsonObject else JsonObject()
        diag.addProperty("export_frame_count",frames);diag.addProperty("invalid_export_frames",invalid);diag.addProperty("smoothing",settings.smoothing)
        diag.addProperty("raw_translation_jitter_mm",rawMetrics.inputTranslationJitterMm);diag.addProperty("raw_rotation_jitter_deg",rawMetrics.inputRotationJitterDeg)
        diag.addProperty("fused_translation_jitter_mm",filmRefinement.inputTranslationJitterMm);diag.addProperty("fused_rotation_jitter_deg",filmRefinement.inputRotationJitterDeg)
        diag.addProperty("refined_translation_jitter_mm",filmRefinement.refinedTranslationJitterMm);diag.addProperty("refined_rotation_jitter_deg",filmRefinement.refinedRotationJitterDeg)
        diag.addProperty("refinement_rejected_outliers",filmRefinement.rejectedOutliers);diag.addProperty("refinement_repaired_samples",filmRefinement.repairedSamples)
        diag.addProperty("refinement_repaired_gaps",filmRefinement.repairedGaps);diag.addProperty("refinement_longest_repaired_gap_ms",filmRefinement.longestRepairedGapMs)
        diag.addProperty("refinement_unrepaired_gaps",filmRefinement.unrepairedGaps);diag.addProperty("refinement_longest_unrepaired_gap_ms",filmRefinement.longestUnrepairedGapMs)
        diag.addProperty("refinement_processing_ms",filmRefinement.processingMs);diag.addProperty("refinement_maximum_translation_deviation_mm",filmRefinement.maximumTranslationDeviationMm)
        diag.addProperty("refinement_maximum_rotation_deviation_deg",filmRefinement.maximumRotationDeviationDeg)
        diag.addProperty("raw_sample_count",rawMetrics.inputSamples);diag.addProperty("fused_sample_count",filmRefinement.inputSamples);diag.addProperty("refined_sample_count",filmRefinement.samplesOrInput())
        val validRatio=if(filmRefinement.inputSamples>0)filmRefinement.validInputSamples.toDouble()/filmRefinement.inputSamples else 0.0
        val stability=1-((filmRefinement.refinedTranslationJitterMm/20).coerceIn(0.0,1.0)+(filmRefinement.refinedRotationJitterDeg/2).coerceIn(0.0,1.0))/2
        diag.addProperty("track_quality_percent",(validRatio*60+stability*40).coerceIn(0.0,100.0))
        diagnostics.writeText(gson.toJson(diag))
        File(dir,"README.txt").writeText("RigTrack vfxtrack v1. UTF-8 CSV, meters, quaternion xyzw, integer original nanoseconds. T_A_B maps B to A. Blender pose = Rx(+90deg) * anchor-relative pose. RAW is untouched AR/rig data; FUSED contains real-time world-lock correction and is never offline-filtered; REFINED is a separate offline derivative with outlier flags, bounded internal-gap repair and centered zero-phase smoothing. blender_reference_camera*.csv is the physical phone camera; blender_camera*.csv is the rig-derived film camera. Invalid rows have empty pose; no leading, trailing or long-gap extrapolation is performed. Importer defaults to REFINED and explicitly falls back to FUSED then RAW. AR clock alignment uses receipt offset, NOT measured exposure/video sync. SYNC timestamps represent cue requests, not physical audio/light onset. Reference projection uses ARCore CPU-image intrinsics adapted to recorded-video dimensions and rotation. See project README for workflow and limits.")
    }
    private fun TrackRefiner.Metrics.samplesOrInput()=validInputSamples-rejectedOutliers+repairedSamples
    private fun writeFilmTrack(input:File,output:File,settings:ShotSettings,start:Long,stop:Long,offset:Long) {
        output.bufferedWriter().use {w->
            w.appendLine("frame,time_s,tx,ty,tz,qx,qy,qz,qw,focal_length_mm,sensor_width_mm,quality,valid,interpolated_gap")
            val source=TrackRefiner.read(input).asSequence().map{it.copy(timestampNs=it.timestampNs+offset)}
            for(f in Resampler.frames(source,settings.film.fps,start,stop,settings.gapMaxMs*1_000_000L)){
                val pose=f.pose?.let(CoordinateConversion::toBlenderCamera)
                w.appendLine(Csv.row(*(listOf(f.index,f.timeS)+(pose?.let(Csv::pose)?:List<Any?>(7){null})+listOf(settings.film.focalLengthMm,settings.film.sensorWidthMm,f.quality,pose!=null,f.interpolatedGap)).toTypedArray()))
            }
        }
    }
    private fun deriveRigTrack(input:File,output:File,rig:Rigid){
        output.bufferedWriter().use{w->w.appendLine("timestamp_ns,time_s,tx,ty,tz,qx,qy,qz,qw,quality,valid,repaired_gap,outlier_rejected")
            input.bufferedReader().use{r->for(v in r.lineSequence().drop(1).filter{it.isNotBlank()}.map(Csv::parse)){
                val pose=if(v[2].isNotEmpty())Csv.rigid(v,2)*rig else null
                w.appendLine(Csv.row(*(listOf(v[0],v[1])+(pose?.let(Csv::pose)?:List<Any?>(7){null})+listOf(v[9],pose!=null,v[11],v[12])).toTypedArray()))
            }}
        }
    }
    private fun writeReferenceTrack(input:File,output:File,settings:ShotSettings,start:Long,stop:Long,offset:Long,arPose:Boolean) {
        output.bufferedWriter().use {w->
            w.appendLine("frame,time_s,tx,ty,tz,qx,qy,qz,qw,quality,valid,interpolated_gap")
            input.bufferedReader().use {r->
                val source=r.lineSequence().drop(1).filter{it.isNotBlank()}.map(Csv::parse).map {v->
                    if(arPose) TimedPose(v[0].toLong()+offset,if(v[21].isNotEmpty())Csv.rigid(v,21)else Rigid(),v[29].toBoolean(),v[28].toDouble())
                    else TimedPose(v[0].toLong()+offset,if(v[2].isNotEmpty())Csv.rigid(v,2)else Rigid(),v[10].toBoolean(),v[9].toDouble())
                }
                for(f in Resampler.frames(source,settings.film.fps,start,stop,settings.gapMaxMs*1_000_000L)) {
                    val pose=f.pose?.let(CoordinateConversion::toBlenderCamera)
                    w.appendLine(Csv.row(*(listOf(f.index,f.timeS)+(pose?.let(Csv::pose)?:List<Any?>(7){null})+listOf(f.quality,pose!=null,f.interpolatedGap)).toTypedArray()))
                }
            }
        }
    }
    private fun addCameraMapping(meta:JsonObject,dir:File,settings:ShotSettings) {
        val intrinsics=File(dir,"intrinsics.csv").takeIf{it.isFile}?.bufferedReader()?.use {r->
            r.lineSequence().drop(1).firstOrNull{it.isNotBlank()}?.let(Csv::parse)
        }
        val calibration=File(dir,"calibration.json").takeIf{it.isFile}?.let {runCatching{JsonParser.parseString(it.readText()).asJsonObject}.getOrNull()}
        val reference=JsonObject().apply {
            addProperty("source","phone_physical_camera");addProperty("poseSource","blender_reference_camera.csv");addProperty("rawPoseSource","blender_reference_camera_raw.csv");addProperty("refinedPoseSource","blender_reference_camera_refined.csv")
            addProperty("intrinsicsSource","intrinsics.csv: ARCore unrotated CPU image")
            addProperty("video","reference_video.mp4");addProperty("videoStartTimestampSource","reference_video_start_timestamp_ns")
            addProperty("videoRotationDegrees",meta["reference_video_rotation_degrees"]?.takeUnless{it.isJsonNull}?.asInt?:0)
            addProperty("videoOrientation",meta["reference_video_orientation"]?.takeUnless{it.isJsonNull}?.asString?:"unknown")
            addProperty("intrinsicsAdaptation", "scale_when_aspect_matches; otherwise_center_crop_then_scale_assumption; then_clockwise_rotation")
            if(intrinsics!=null&&intrinsics.size>=7){
                addProperty("fx",intrinsics[1].toDouble());addProperty("fy",intrinsics[2].toDouble());addProperty("cx",intrinsics[3].toDouble());addProperty("cy",intrinsics[4].toDouble())
                addProperty("intrinsicsWidth",intrinsics[5].toInt());addProperty("intrinsicsHeight",intrinsics[6].toInt())
            }
            meta["reference_video_width"]?.takeUnless{it.isJsonNull}?.let{addProperty("videoWidth",it.asInt)}
            meta["reference_video_height"]?.takeUnless{it.isJsonNull}?.let{addProperty("videoHeight",it.asInt)}
            calibration?.getAsJsonArray("distortion")?.let{add("distortion",it.deepCopy())}
        }
        val film=JsonObject().apply {
            addProperty("source","rig_derived");addProperty("poseSource","blender_camera.csv");addProperty("rawPoseSource","blender_camera_raw.csv");addProperty("refinedPoseSource","blender_camera_refined.csv")
            addProperty("model",settings.film.name);addProperty("sensorWidthMm",settings.film.sensorWidthMm);addProperty("sensorHeightMm",settings.film.sensorHeightMm)
            addProperty("focalLengthMm",settings.film.focalLengthMm);addProperty("width",settings.film.width);addProperty("height",settings.film.height)
        }
        meta.add("referenceCamera",reference);meta.add("filmCamera",film)
    }
    private fun packageFiles(dir:File)=dir.listFiles().orEmpty().filter{it.isFile&&!it.name.endsWith(".tmp")&&it.name!="exported.flag"&&it.name!="metadata.json"&&it.name!="reference_video.mp4"&&it.name!="reference_video.partial.mp4"}.sortedBy{it.name}
    fun estimate(dir:File,includeReferenceVideo:Boolean=false):ExportEstimate {
        val video=File(dir,"reference_video.mp4").takeIf{it.isFile&&it.length()>0}
        val tracking=packageFiles(dir).sumOf{it.length()}+File(dir,"metadata.json").takeIf{it.isFile}?.length().orZero()
        val videoBytes=if(includeReferenceVideo)video?.length()?:0 else 0
        return ExportEstimate(tracking,videoBytes,tracking+videoBytes,video!=null)
    }
    private fun Long?.orZero()=this?:0L
    private fun packageMetadata(dir:File,includeReferenceVideo:Boolean):ByteArray {
        val source=File(dir,"metadata.json")
        val meta=if(source.isFile)runCatching{JsonParser.parseString(source.readText()).asJsonObject}.getOrElse{JsonObject()}else JsonObject()
        val video=File(dir,"reference_video.mp4").takeIf{it.isFile&&it.length()>0}
        val included=includeReferenceVideo&&video!=null
        meta.addProperty("referenceVideoAvailable",video!=null)
        meta.addProperty("referenceVideoIncludedInPackage",included)
        if(included)meta.addProperty("referenceVideoPath","reference_video.mp4")else meta.add("referenceVideoPath",JsonNull.INSTANCE)
        meta.addProperty("referenceVideoFilename","reference_video.mp4")
        meta.addProperty("referenceVideoSizeBytes",video?.length()?:0L)
        for((camel,snake) in listOf("referenceVideoDuration" to "reference_video_duration","referenceVideoResolution" to "reference_video_resolution","referenceVideoFps" to "reference_video_fps","referenceVideoCodec" to "reference_video_codec","referenceVideoRotationDegrees" to "reference_video_rotation_degrees","referenceVideoOrientation" to "reference_video_orientation"))if(!meta.has(camel))meta.add(camel,meta.get(snake)?:JsonNull.INSTANCE)
        meta.addProperty("reference_video_available",video!=null)
        meta.addProperty("reference_video_included_in_package",included)
        if(included)meta.addProperty("reference_video_path","reference_video.mp4")else meta.add("reference_video_path",JsonNull.INSTANCE)
        meta.addProperty("reference_video_filename","reference_video.mp4")
        meta.addProperty("reference_video_size_bytes",video?.length()?:0L)
        return gson.toJson(meta).toByteArray(Charsets.UTF_8)
    }
    fun zip(dir:File,output:OutputStream,includeReferenceVideo:Boolean=false) {
        val video=File(dir,"reference_video.mp4").takeIf{includeReferenceVideo&&it.isFile&&it.length()>0}
        ZipOutputStream(BufferedOutputStream(output,1024*1024)).use{zip->
            zip.putNextEntry(ZipEntry("metadata.json"));zip.write(packageMetadata(dir,includeReferenceVideo));zip.closeEntry()
            packageFiles(dir).forEach{file->zip.putNextEntry(ZipEntry(file.name));file.inputStream().buffered(1024*1024).use{it.copyTo(zip,1024*1024)};zip.closeEntry()}
            video?.let{file->zip.putNextEntry(ZipEntry(file.name));file.inputStream().buffered(1024*1024).use{it.copyTo(zip,1024*1024)};zip.closeEntry()}
        }
    }
}
