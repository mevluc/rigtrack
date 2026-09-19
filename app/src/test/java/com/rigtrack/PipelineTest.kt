package com.rigtrack

import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import com.rigtrack.export.*
import com.rigtrack.recording.*
import com.rigtrack.tracking.fusion.*
import com.rigtrack.tracking.arcore.SyntheticTracker
import com.rigtrack.tracking.marker.CpuImage
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.zip.ZipFile

class PipelineTest {
    @Test fun streamingWriterZipAndSyntheticTrajectory()=runBlocking {
        val root=kotlin.io.path.createTempDirectory("rigtrack-test").toFile()
        try {
            val start=9_123_456_789_123_456L
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val w=SessionWriter(File(root,"take.partial"),ShotSettings(synthetic=true),start,emptyMap(),scope){error(it)}
            for(i in 0..180){val ns=start+i*1_000_000_000L/60;val pose=SyntheticTracker.trajectory(i/60.0,"Test trajectory");w.ar(ArPoseSample(ns,ns,ns,pose,Rigid(),"TRACKING","NONE",Intrinsics(900.0,900.0,640.0,360.0,1280,720)),pose);w.imu(ImuSample(ns,ns,4,listOf(0f,0f,0f),3))}
            w.event(start+1_000_000_000,"SYNC_001")
            val dir=w.stop(start+3_000_000_000,extraMetadata=mapOf("referenceVideoAvailable" to false,"reference_video_enabled" to false))
            val rows=File(dir,"blender_camera.csv").readLines().drop(1).map(Csv::parse)
            val referenceRows=File(dir,"blender_reference_camera.csv").readLines().drop(1).map(Csv::parse)
            assertEquals(76,rows.size);assertEquals("true",rows.first()[12]);val second=Csv.rigid(rows[25],2);assertEquals(1.0,second.t.y,1e-8)
            assertEquals(rows.size,referenceRows.size);assertEquals(Csv.rigid(rows[25],2),Csv.rigid(referenceRows[25],2))
            val third=Csv.rigid(rows[50],2);assertEquals(1.0,third.t.x,1e-8);assertEquals(1.0,third.t.y,1e-8)
            val yaw=Csv.rigid(rows[75],2);assertTrue(yaw.q.angle(CoordinateConversion.toBlenderCamera(SyntheticTracker.trajectory(3.0,"Test trajectory")).q)<1e-8)
            assertEquals(start.toString(),Csv.parse(File(dir,"ar_pose.csv").readLines()[1])[0])
            val zip=File(root,"take.vfxtrack");TrackExporter.zip(dir,zip.outputStream());ZipFile(zip).use{z->for(name in listOf("metadata.json","calibration.json","rig.json","marker_map.json","ar_pose.csv","film_camera_raw.csv","film_camera_fused.csv","film_camera_refined.csv","phone_camera_refined.csv","imu.csv","markers.csv","intrinsics.csv","events.csv","diagnostics.json","blender_camera.csv","blender_camera_raw.csv","blender_camera_refined.csv","blender_reference_camera.csv","blender_reference_camera_raw.csv","blender_reference_camera_refined.csv","README.txt"))assertNotNull(name,z.getEntry(name))}
            val metadata=JsonParser.parseString(File(dir,"metadata.json").readText()).asJsonObject
            assertEquals(1,metadata["format_version"].asInt);assertFalse(metadata["referenceVideoAvailable"].asBoolean)
            assertEquals("phone_physical_camera",metadata.getAsJsonObject("referenceCamera")["source"].asString)
            assertEquals("blender_reference_camera.csv",metadata.getAsJsonObject("referenceCamera")["poseSource"].asString)
            assertEquals("blender_reference_camera_refined.csv",metadata.getAsJsonObject("referenceCamera")["refinedPoseSource"].asString)
            assertEquals(900.0,metadata.getAsJsonObject("referenceCamera")["fx"].asDouble,0.0)
            assertEquals("rig_derived",metadata.getAsJsonObject("filmCamera")["source"].asString)
            assertEquals("Sony FX3",metadata.getAsJsonObject("filmCamera")["model"].asString)
            assertEquals("film_camera_refined.csv",metadata.getAsJsonObject("refinement")["refinedSource"].asString)
            val fixture=System.getProperty("rigtrack.fixture")?.let(::File);if(fixture!=null){fixture.parentFile?.mkdirs();zip.copyTo(fixture,true)}
            System.getProperty("rigtrack.fixture.withvideo")?.let(::File)?.let{fixtureWithVideo->
                fixtureWithVideo.parentFile?.mkdirs();File(dir,ReferenceVideoRecorder.FILENAME).writeBytes(ByteArray(4096){(it%251).toByte()});fixtureWithVideo.outputStream().use{TrackExporter.zip(dir,it,true)}
            }
            scope.cancel()
        }finally{root.deleteRecursively()}
    }
    @Test fun gapsNeverExtrapolate(){val poses=sequenceOf(TimedPose(0,Rigid()),TimedPose(20_000_000,Rigid(),false),TimedPose(500_000_000,Rigid(V3(1.0,0.0,0.0))))
        val rows=Resampler.frames(poses,Fps(25),0,600_000_000,100_000_000).toList();assertNotNull(rows[0].pose);assertNull(rows[1].pose);assertNull(rows.last().pose)}
    @Test fun shortGapIsFlagged(){val poses=sequenceOf(TimedPose(0,Rigid()),TimedPose(20_000_000,Rigid(),false),TimedPose(80_000_000,Rigid(V3(1.0,0.0,0.0))));val rows=Resampler.frames(poses,Fps(25),0,80_000_000,100_000_000).toList();assertTrue(rows[1].interpolatedGap);assertEquals(.5,rows[1].pose!!.t.x,1e-8)}
    @Test fun robustOutlierRejected(){val result=RobustPose.estimate(listOf(RobustPose.Weighted(Rigid(V3(1.0,0.0,0.0)),1.0),RobustPose.Weighted(Rigid(V3(1.01,0.0,0.0)),1.0),RobustPose.Weighted(Rigid(V3(10.0,5.0,0.0),Q.eulerXYZ(2.0,1.0,0.0)),1.0)))!!;assertEquals(1.005,result.t.x,1e-8)}
    @Test fun rotationAxes(){val basis=CoordinateConversion.T_blenderWorld_anchor
        for(axis in listOf(V3(1.0,0.0,0.0),V3(0.0,1.0,0.0),V3(0.0,0.0,1.0))){val pose=Rigid(q=Q.axis(axis,Math.PI/2));val result=CoordinateConversion.toBlenderCamera(pose);for(v in listOf(V3(1.0,0.0,0.0),V3(0.0,1.0,0.0),V3(0.0,0.0,-1.0)))assertTrue((result.q.rotate(v)-basis.q.rotate(pose.q.rotate(v))).norm()<1e-8)}}
    @Test fun referenceTrackStaysAtPhoneWhileFilmTrackUsesRigOffset()=runBlocking {
        val root=kotlin.io.path.createTempDirectory("two-camera").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val start=1_000_000_000L
            val rig=Rigid(V3(.05,.10,-.02),Q.axis(V3(0.0,1.0,0.0),.1))
            val settings=ShotSettings(synthetic=true,rig=RigProfile("Measured",rig))
            val writer=SessionWriter(File(root,"take.partial"),settings,start,emptyMap(),scope){error(it)}
            for(i in 0..30){val ns=start+i*20_000_000L;writer.ar(ArPoseSample(ns,ns,ns,Rigid(),Rigid(),"TRACKING","NONE",Intrinsics(1000.0,1000.0,960.0,540.0,1920,1080)),Rigid())}
            val dir=writer.stop(start+500_000_000L)
            val reference=Csv.rigid(File(dir,"blender_reference_camera.csv").readLines().drop(1).map(Csv::parse)[5],2)
            val film=Csv.rigid(File(dir,"blender_camera.csv").readLines().drop(1).map(Csv::parse)[5],2)
            val refinedReference=Csv.rigid(File(dir,"blender_reference_camera_refined.csv").readLines().drop(1).map(Csv::parse)[5],2)
            val refinedFilm=Csv.rigid(File(dir,"blender_camera_refined.csv").readLines().drop(1).map(Csv::parse)[5],2)
            assertTrue(reference.t.norm()<1e-10);assertTrue((film.t-reference.t).norm()>.10)
            assertTrue(film.q.angle(reference.q)>.09)
            val expected=CoordinateConversion.toBlenderCamera(CoordinateConversion.T_blenderWorld_anchor.inverse()*refinedReference*rig)
            assertTrue((refinedFilm.t-expected.t).norm()<1e-9);assertTrue(refinedFilm.q.angle(expected.q)<1e-9)
        } finally {scope.cancel();root.deleteRecursively()}
    }
    @Test fun futureFormatRejected(){val root=kotlin.io.path.createTempDirectory("future").toFile();try{File(root,"metadata.json").writeText("{\"format_version\":2}");assertThrows(IllegalArgumentException::class.java){TrackExporter.process(root)}}finally{root.deleteRecursively()}}
    @Test fun referenceVideoExportIsOptionalAndPackageMetadataMatchesContents(){
        val root=kotlin.io.path.createTempDirectory("reference-export").toFile()
        try {
            File(root,"metadata.json").writeText("{\"referenceVideoDuration\":1.25,\"referenceVideoResolution\":\"1280x720\",\"reference_video_codec\":\"video/avc\"}")
            File(root,"markers.csv").writeText("header\n")
            val video=ByteArray(32){it.toByte()};File(root,ReferenceVideoRecorder.FILENAME).writeBytes(video)
            val estimateOff=TrackExporter.estimate(root,false);val estimateOn=TrackExporter.estimate(root,true)
            assertTrue(estimateOff.videoAvailable);assertEquals(0,estimateOff.videoBytes);assertEquals(32,estimateOn.videoBytes);assertEquals(estimateOff.totalBytes+32,estimateOn.totalBytes)
            for(include in listOf(false,true)){
                val displayName=ExportDocuments.spec("shot_001","zip").displayName
                val zip=File(root.parentFile,"${if(include) "with-video" else "no-video"}-$displayName")
                try {
                    assertTrue(zip.name.endsWith(".vfxtrack"));assertFalse(zip.name.endsWith(".zip"));assertFalse(zip.name.endsWith(".vfxtrack.zip"))
                    TrackExporter.zip(root,zip.outputStream(),include)
                    assertArrayEquals(byteArrayOf(0x50,0x4b,0x03,0x04),zip.inputStream().use{it.readNBytes(4)})
                    ZipFile(zip).use { archive ->
                    val entry=archive.getEntry(ReferenceVideoRecorder.FILENAME)
                    if(include){assertNotNull(entry);assertArrayEquals(video,archive.getInputStream(entry).readBytes())}else assertNull(entry)
                    val packaged=JsonParser.parseReader(archive.getInputStream(archive.getEntry("metadata.json")).reader()).asJsonObject
                    assertTrue(packaged["referenceVideoAvailable"].asBoolean);assertEquals(include,packaged["referenceVideoIncludedInPackage"].asBoolean);assertEquals(32,packaged["referenceVideoSizeBytes"].asInt)
                    assertEquals("video/avc",packaged["referenceVideoCodec"].asString);assertEquals(1.25,packaged["referenceVideoDuration"].asDouble,0.0)
                    if(include)assertEquals(ReferenceVideoRecorder.FILENAME,packaged["referenceVideoPath"].asString)else assertTrue(packaged["referenceVideoPath"].isJsonNull)
                    assertNotNull(archive.getEntry("markers.csv"))
                } } finally { zip.delete() }
            }
            val source=JsonParser.parseString(File(root,"metadata.json").readText()).asJsonObject
            assertNull(source["referenceVideoIncludedInPackage"])
        } finally { root.deleteRecursively() }
    }
    @Test fun cpuLumaCopyHonorsPaddingPixelStrideAndCrop(){
        val padded=ByteArray(2048*2){0x55};for(y in 0..1)for(x in 0 until 1920)padded[y*2048+x]=((x+y)%251).toByte()
        val fullHdRows=CpuImage.copyLuma(java.nio.ByteBuffer.wrap(padded),1920,2,2048,1)
        assertEquals(1920*2,fullHdRows.size);assertEquals(padded[2048+1919],fullHdRows[1920+1919])
        val width=4;val height=3;val rowStride=8;val fullHeight=5;val bytes=ByteArray(rowStride*fullHeight){0x7f}
        for(y in 0 until fullHeight)for(x in 0 until 4)bytes[y*rowStride+x*2]=(y*10+x).toByte()
        val compact=CpuImage.copyLuma(java.nio.ByteBuffer.wrap(bytes),width=3,height=2,rowStride=rowStride,pixelStride=2,cropLeft=1,cropTop=2)
        assertArrayEquals(byteArrayOf(21,22,23,31,32,33),compact)
        val cropped=CpuImage.croppedIntrinsics(Intrinsics(960.0,540.0,480.0,270.0,960,540),1920,1080,120,40,1600,900)
        assertEquals(1920.0,cropped.fx,0.0);assertEquals(500.0,cropped.cy,0.0);assertEquals(1600,cropped.width);assertEquals(900,cropped.height)
    }
    @Test fun generatorAndDetectorShareTheSameDefaultDictionary(){
        assertEquals(MarkerDictionaries.DEFAULT,DetectorSettings().dictionary)
        assertEquals(MarkerDictionaries.DEFAULT,com.rigtrack.printing.MarkerPdf.dictionaries.first().first)
    }
    @Test fun markerSizeOverridesParseInMetersAndRejectBadInput(){
        assertEquals(mapOf(1 to .15,12 to .2),com.rigtrack.ui.parseMarkerSizes("1:150, 12:200"))
        assertThrows(IllegalArgumentException::class.java){com.rigtrack.ui.parseMarkerSizes("1:0")}
        assertThrows(IllegalArgumentException::class.java){com.rigtrack.ui.parseMarkerSizes("bad")}
    }
    @Test fun referenceVideoQualitiesAndStorageBudgetAreExplicit(){
        assertEquals(ReferenceVideoQuality.OFF,ReferenceVideoQuality.parse("off"))
        assertEquals(ReferenceVideoQuality.P720,ReferenceVideoQuality.parse("720p"))
        assertEquals(ReferenceVideoQuality.P1080,ReferenceVideoQuality.parse("1080p"))
        assertEquals(0L,ReferenceVideoRecorder.estimatedBytes(ReferenceVideoQuality.OFF))
        assertTrue(ReferenceVideoRecorder.estimatedBytes(ReferenceVideoQuality.P1080)>ReferenceVideoRecorder.estimatedBytes(ReferenceVideoQuality.P720))
    }
    @Test fun vfxtrackDocumentNameNeverAcquiresZipExtension(){
        for(source in listOf("shot_001","shot_001.vfxtrack","shot_001.vfxtrack.zip")){
            val spec=ExportDocuments.spec(source,"zip")
            assertEquals("shot_001.vfxtrack",spec.displayName)
            assertTrue(spec.displayName.endsWith(".vfxtrack"));assertFalse(spec.displayName.endsWith(".vfxtrack.zip"))
            assertEquals("application/vnd.rigtrack.vfxtrack",spec.mimeType);assertNotEquals("application/zip",spec.mimeType)
        }
        val session=kotlin.io.path.createTempDirectory("1788785949449").toFile()
        try {
            File(session,"metadata.json").writeText("""{"shot":{"name":"shot_001"}}""")
            assertEquals("shot_001.vfxtrack",ExportDocuments.spec(session,"zip").displayName)
            assertEquals("shot_001_blender_camera.csv",ExportDocuments.spec(session,"csv").displayName)
        } finally { session.deleteRecursively() }
    }
    @Test fun worldLockDoesNotSnapAndFreezesWhenLost(){
        val lock=WorldLock();lock.active=true
        val k=Intrinsics(900.0,900.0,640.0,360.0,1280,720)
        fun sample(ns:Long,state:String="TRACKING")=ArPoseSample(ns,ns,ns,Rigid(),Rigid(),state,"NONE",k)
        val marker=Rigid(V3(0.0,0.0,-2.0))
        val measuredCamera=Rigid(V3(.1,0.0,0.0))
        val o=MarkerObservation(0,0,0,12,.15,List(8){100.0},V3(),V3(),measuredCamera.inverse()*marker,.1,10000.0,0.0,.9,k,true)
        val map=MarkerMap(references=listOf(MarkerReference(12,.15,marker,30)))
        val settings=DetectorSettings();lock.apply(sample(1),.12,settings)
        for(i in 1..4)lock.observe(sample(i*16_000_000L),listOf(o.copy(timestampNs=i*16_000_000L)),map,settings)
        val first=lock.apply(sample(80_000_000),.12,settings)!!;assertTrue(first.t.x>0&&first.t.x<.01)
        var settled=first;for(i in 2..200)settled=lock.apply(sample(i*16_000_000L),.12)!!
        assertTrue(settled.t.x>.09)
        val paused=lock.apply(sample(3_500_000_000,"PAUSED"),.12)!!;assertEquals(settled.t.x,paused.t.x,1e-12)
    }
    @Test fun recoveryRetainsOriginalPartial()=runBlocking {
        val root=kotlin.io.path.createTempDirectory("recovery").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try{val w=SessionWriter(File(root,"shot.partial"),ShotSettings(synthetic=true),1000,emptyMap(),scope){error(it)}
            for(i in 0..10){val ns=1000+i*40_000_000L;w.ar(ArPoseSample(ns,ns,ns,Rigid(),Rigid(),"TRACKING","NONE",Intrinsics(1.0,1.0,0.0,0.0,10,10)),Rigid())}
            val complete=w.stop(400_001_000);val partial=File(root,"interrupted.partial");complete.copyRecursively(partial)
            val raw=File(partial,"film_camera_raw.csv");raw.appendText("incomplete");val original=raw.readBytes()
            val recovered=Recovery.recover(partial);assertArrayEquals(original,raw.readBytes());assertFalse(File(recovered,"film_camera_raw.csv").readText().contains("incomplete"));assertTrue(File(recovered,"blender_camera.csv").exists())
        }finally{scope.cancel();root.deleteRecursively()}
    }
}
