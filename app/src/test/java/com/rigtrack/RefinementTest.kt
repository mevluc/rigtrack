package com.rigtrack

import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import com.rigtrack.export.TrackRefiner
import com.rigtrack.tracking.fusion.WorldLock
import org.junit.Assert.*
import org.junit.Test

class RefinementTest {
    private val k=Intrinsics(900.0,900.0,640.0,360.0,1280,720)
    private fun sample(ns:Long)=ArPoseSample(ns,ns,ns,Rigid(),Rigid(),"TRACKING","NONE",k)
    private fun observation(id:Int,ns:Long,marker:Rigid,camera:Rigid,confidence:Double=.9)=MarkerObservation(ns,ns,ns,id,.15,List(8){100.0},V3(),V3(),camera.inverse()*marker,.1,10000.0,0.0,confidence,k,true)
    private fun acquire(camera:Rigid,settings:DetectorSettings=DetectorSettings()):Pair<WorldLock,Rigid>{
        val lock=WorldLock().also{it.active=true};val marker=Rigid(V3(0.0,0.0,-2.0));val map=MarkerMap(references=listOf(MarkerReference(1,.15,marker,60)))
        for(i in 1..settings.relockConfirmationFrames){val ns=i*16_000_000L;lock.observe(sample(ns),listOf(observation(1,ns,marker,camera)),map,settings)}
        return lock to lock.apply(sample((settings.relockConfirmationFrames*16+16)*1_000_000L),.12,settings)!!
    }

    @Test fun translationAndRotationDeadZonesAreIndependent(){
        val settings=DetectorSettings(translationDeadZoneMm=3.0,rotationDeadZoneDeg=.15,relockConfirmationFrames=4)
        val (_,small)=acquire(Rigid(V3(.002,0.0,0.0),Q.axis(V3(0.0,1.0,0.0),Math.toRadians(.1))),settings)
        assertEquals(0.0,small.t.norm(),1e-12);assertEquals(0.0,Math.toDegrees(small.q.angle(Q())),1e-9)
        val (translationLock,early)=acquire(Rigid(V3(.005,0.0,0.0)),settings);assertTrue(early.t.x>0&&early.t.x<.005)
        val translated=translationLock.apply(sample(500_000_000),.12,settings)!!;assertEquals(.005,translated.t.x,1e-6)
        val (rotationLock,_)=acquire(Rigid(q=Q.axis(V3(0.0,1.0,0.0),Math.toRadians(.5))),settings)
        val rotated=rotationLock.apply(sample(600_000_000),.12,settings)!!;assertEquals(.5,Math.toDegrees(rotated.q.angle(Q())),1e-4)
    }

    @Test fun relockNeedsFourConsistentFramesAndBlends(){
        val settings=DetectorSettings(relockConfirmationFrames=4);val lock=WorldLock().also{it.active=true};val marker=Rigid(V3(0.0,0.0,-2.0));val map=MarkerMap(references=listOf(MarkerReference(1,.15,marker,60)));val camera=Rigid(V3(.04,0.0,0.0))
        val first=lock.observe(sample(1_000_000_000),listOf(observation(1,1_000_000_000,marker,camera)),map,settings)
        assertEquals(MarkerRejectionReason.AWAITING_CONFIRMATION,first.getValue(1).rejectionReason);assertEquals(0.0,lock.apply(sample(1_010_000_000),.12,settings)!!.t.x,0.0)
        for(i in 2..4){val ns=1_000_000_000+i*16_000_000L;lock.observe(sample(ns),listOf(observation(1,ns,marker,camera)),map,settings)}
        val blended=lock.apply(sample(1_080_000_000),.12,settings)!!.t.x;assertTrue(blended in 0.0..0.04);assertTrue(blended>0)
    }

    @Test fun multiMarkerOutlierIsRejectedAndMovedMarkerIsQuarantined(){
        val settings=DetectorSettings(relockConfirmationFrames=1,movedConfirmationFrames=5);val lock=WorldLock().also{it.active=true}
        val refs=(1..3).map{MarkerReference(it,.15,Rigid(V3(it.toDouble(),0.0,-2.0)),60)};val map=MarkerMap(references=refs)
        repeat(5){n->val ns=(n+1)*20_000_000L;val found=refs.map{r->observation(r.id,ns,r.T_anchor_marker,if(r.id==3)Rigid(V3(.10,0.0,0.0))else Rigid())};val decisions=lock.observe(sample(ns),found,map,settings);assertFalse(decisions.getValue(3).usedForCorrection)}
        assertTrue(3 in lock.movedMarkerIds);assertEquals(MarkerRejectionReason.MARKER_MOVED,lock.observe(sample(140_000_000),listOf(observation(3,140_000_000,refs[2].T_anchor_marker,Rigid())),map,settings).getValue(3).rejectionReason)
        assertTrue(lock.apply(sample(500_000_000),.12,settings)!!.t.norm()<1e-6)
    }

    @Test fun shortGapRepairsButLongGapDoesNot(){
        fun p(ns:Long,x:Double,valid:Boolean=true)=TimedPose(ns,Rigid(V3(x,0.0,0.0)),valid)
        val short=TrackRefiner.refine(listOf(p(0,0.0),p(25_000_000,.025),p(50_000_000,0.0,false),p(75_000_000,.075)),"Off",50_000_000)
        assertTrue(short.samples[2].repairedGap);assertEquals(.05,short.samples[2].pose!!.t.x,1e-9)
        val long=TrackRefiner.refine(listOf(p(0,0.0),p(20_000_000,.02),p(100_000_000,0.0,false),p(220_000_000,.22)),"Off",100_000_000)
        assertNull(long.samples[2].pose);assertFalse(long.samples[2].repairedGap);assertEquals(1,long.metrics.unrepairedGaps);assertEquals(200.0,long.metrics.longestUnrepairedGapMs,0.0)
    }

    @Test fun isolatedSpikeAndQuaternionSignAreHandled(){
        val input=(0..10).map{i->TimedPose(i*20_000_000L,Rigid(V3(if(i==5)1.0 else i*.001,0.0,0.0),if(i==6)-Q() else Q()))}
        val result=TrackRefiner.refine(input,"Low",100_000_000)
        assertEquals(1,result.metrics.rejectedOutliers);assertTrue(result.samples[5].outlierRejected);assertTrue(result.samples[5].repairedGap)
        assertTrue(result.samples.all{it.pose==null||it.pose.q.angle(Q())<1e-9})
    }

    @Test fun centeredFilterHasNoPhaseShiftAndPreservesFastPanEndpoints(){
        val input=(0..20).map{i->TimedPose(i*20_000_000L,Rigid(V3(i*.10+(if(i%2==0).002 else -.002),0.0,0.0),Q.axis(V3(0.0,1.0,0.0),i*.02)))}
        val result=TrackRefiner.refine(input,"High",100_000_000)
        val center=result.samples[10].pose!!;assertEquals(1.002,center.t.x,.01)
        assertEquals(input.first().pose.t.x,result.samples.first().pose!!.t.x,.01);assertEquals(input.last().pose.t.x,result.samples.last().pose!!.t.x,.01)
        assertTrue(result.metrics.refinedTranslationJitterMm<result.metrics.inputTranslationJitterMm)
    }
}
