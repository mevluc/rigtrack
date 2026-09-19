package com.rigtrack.tracking.fusion

import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import kotlin.math.*

object RobustPose {
    data class Weighted(val pose:Rigid,val weight:Double)
    fun estimate(candidates:List<Weighted>,translationGate:Double=.12,angleGate:Double=Math.toRadians(12.0)):Rigid? {
        val valid=candidates.filter{it.weight>0&&it.weight.isFinite()};if(valid.isEmpty())return null
        fun median(values:List<Double>)=values.sorted().let{if(it.size%2==0)(it[it.size/2-1]+it[it.size/2])/2 else it[it.size/2]}
        val center=V3(median(valid.map{it.pose.t.x}),median(valid.map{it.pose.t.y}),median(valid.map{it.pose.t.z}))
        val rotation=valid.minBy{a->valid.sumOf{b->a.pose.q.angle(b.pose.q)*b.weight}}.pose.q
        val inliers=valid.filter{(it.pose.t-center).norm()<=translationGate&&it.pose.q.angle(rotation)<=angleGate}
        if(inliers.isEmpty()||inliers.size*2<valid.size)return null
        val weight=inliers.sumOf{it.weight};val t=inliers.fold(V3()){v,p->v+p.pose.t*(p.weight/weight)}
        // Hemisphere-aligned quaternion mean; outliers removed before averaging.
        val a=DoubleArray(4)
        for(p in inliers){val q=if(p.pose.q.dot(rotation)<0)-p.pose.q else p.pose.q;a[0]+=q.x*p.weight;a[1]+=q.y*p.weight;a[2]+=q.z*p.weight;a[3]+=q.w*p.weight}
        return Rigid(t,Q(a[0],a[1],a[2],a[3]).normalized())
    }
}

class WorldLock {
    enum class State { UNLOCKED,CANDIDATE,CONFIRMED,BLENDING,LOCKED }
    private data class Candidate(val observation:MarkerObservation,val reference:MarkerReference,val camera:Rigid,val correction:Rigid,val weight:Double)
    private var correction=Rigid();private var target=Rigid();private var blendStart=Rigid();private var lastApplyNs=0L
    private var blendStartNs=0L;private var lastAcceptedNs=0L;private var pending:Rigid?=null;private var confirmations=0
    private val movedCounts=mutableMapOf<Int,Int>();private val movedMarkers=mutableSetOf<Int>()
    @Volatile var confidence=0.0;private set
    @Volatile var corrections=0L;private set
    @Volatile var rejected=0L;private set
    @Volatile var usedObservations=0L;private set
    @Volatile var relocks=0L;private set
    @Volatile var state=State.UNLOCKED;private set
    val rejectionCounts=mutableMapOf<MarkerRejectionReason,Long>()
    val movedMarkerIds:Set<Int> get()=synchronized(this){movedMarkers.toSet()}
    @Volatile var active=false
    @Synchronized fun reset(){correction=Rigid();target=Rigid();blendStart=Rigid();lastApplyNs=0;blendStartNs=0;lastAcceptedNs=0;pending=null;confirmations=0;confidence=0.0;corrections=0;rejected=0;usedObservations=0;relocks=0;state=State.UNLOCKED;movedCounts.clear();movedMarkers.clear();rejectionCounts.clear()}
    private fun mapWeight(reference:MarkerReference):Double {
        if((reference.confidence?:1.0)<.4||(reference.translationStddevM?:0.0)>.05||(reference.rotationStddevDeg?:0.0)>3.0||(reference.meanReprojectionError?:0.0)>3.0)return 0.0
        val observations=sqrt((reference.observations/30.0).coerceIn(.1,1.0))
        val spread=1.0/(1.0+(reference.translationStddevM?:0.0)*40+(reference.rotationStddevDeg?:0.0)/3)
        return observations*spread*(reference.confidence?:1.0)
    }
    fun cameraFromMap(observations:List<MarkerObservation>,map:MarkerMap,minConfidence:Double):Rigid? {
        val refs=map.references.associateBy{it.id}
        return RobustPose.estimate(observations.mapNotNull{o->val ref=refs[o.id];val mapWeight=ref?.let(::mapWeight)?:0.0;if(ref==null||o.confidence<minConfidence||mapWeight<=0)null else RobustPose.Weighted(ref.T_anchor_marker*o.T_camera_marker.inverse(),o.confidence*o.confidence*mapWeight)})
    }
    private fun reject(reason:MarkerRejectionReason){rejected++;rejectionCounts[reason]=(rejectionCounts[reason]?:0)+1}
    @Synchronized fun observe(sample:ArPoseSample,observations:List<MarkerObservation>,map:MarkerMap,settings:DetectorSettings):Map<Int,MarkerUseDecision> {
        val decisions=mutableMapOf<Int,MarkerUseDecision>();val refs=map.references.associateBy{it.id}
        if(!active||!sample.valid){for(o in observations)decisions[o.id]=MarkerUseDecision(o.id in refs,false,false,MarkerRejectionReason.ARCORE_NOT_TRACKING);return decisions}
        val relative=sample.relative?:return decisions
        val candidates=mutableListOf<Candidate>()
        for(o in observations){
            val ref=refs[o.id]
            val reason=when {ref==null->MarkerRejectionReason.NOT_MAPPED;o.confidence<settings.minConfidence->MarkerRejectionReason.LOW_CONFIDENCE;o.reprojectionError>settings.maxError->MarkerRejectionReason.HIGH_REPROJECTION_ERROR;o.id in movedMarkers->MarkerRejectionReason.MARKER_MOVED;mapWeight(ref)<=0->MarkerRejectionReason.MAP_QUALITY_LOW;else->null}
            if(reason!=null){decisions[o.id]=MarkerUseDecision(ref!=null,false,false,reason);if(ref!=null)reject(reason);continue}
            val camera=ref!!.T_anchor_marker*o.T_camera_marker.inverse();val proposed=camera*relative.inverse()
            candidates+=Candidate(o,ref,camera,proposed,o.confidence*o.confidence*mapWeight(ref))
        }
        if(candidates.isEmpty())return decisions
        val measured=RobustPose.estimate(candidates.map{RobustPose.Weighted(it.camera,it.weight)},settings.innovationTranslationMm/1000,Math.toRadians(settings.innovationRotationDeg))
            ?:run {for(c in candidates){decisions[c.observation.id]=MarkerUseDecision(true,false,false,MarkerRejectionReason.OUTLIER_TRANSLATION);reject(MarkerRejectionReason.OUTLIER_TRANSLATION)};return decisions}
        val proposed=measured*relative.inverse()
        val inliers=mutableListOf<Candidate>()
        for(c in candidates){
            val dt=(c.camera.t-measured.t).norm();val dr=Math.toDegrees(c.camera.q.angle(measured.q))
            val reason=when {dt>settings.movedTranslationMm/1000->MarkerRejectionReason.OUTLIER_TRANSLATION;dr>settings.movedRotationDeg->MarkerRejectionReason.OUTLIER_ROTATION;else->null}
            if(reason==null){inliers+=c;movedCounts[c.observation.id]=0}
            else {val count=(movedCounts[c.observation.id]?:0)+1;movedCounts[c.observation.id]=count;if(count>=settings.movedConfirmationFrames){movedMarkers+=c.observation.id;decisions[c.observation.id]=MarkerUseDecision(true,false,false,MarkerRejectionReason.MARKER_MOVED,dt,dr);reject(MarkerRejectionReason.MARKER_MOVED)}else{decisions[c.observation.id]=MarkerUseDecision(true,false,false,reason,dt,dr);reject(reason)}}
        }
        if(inliers.isEmpty())return decisions
        val innovationT=(proposed.t-correction.t).norm();val innovationR=Math.toDegrees(proposed.q.angle(correction.q))
        val elapsed=if(lastAcceptedNs==0L)0.0 else (sample.timestampNs-lastAcceptedNs).coerceAtLeast(0)/1e9
        val gateT=settings.innovationTranslationMm/1000+min(.05,elapsed*.05);val gateR=settings.innovationRotationDeg+min(5.0,elapsed*5)
        // Once locked, a lone marker cannot drag the world abruptly.  Initial lock and a
        // genuine re-lock are instead protected by the multi-frame confirmation below.
        val recentlyLocked=lastAcceptedNs!=0L&&sample.timestampNs-lastAcceptedNs<=500_000_000L&&state!=State.CANDIDATE
        if(recentlyLocked&&inliers.size<2&&(innovationT>gateT||innovationR>gateR)){
            val reason=if(innovationT>gateT)MarkerRejectionReason.OUTLIER_TRANSLATION else MarkerRejectionReason.OUTLIER_ROTATION
            for(c in inliers){decisions[c.observation.id]=MarkerUseDecision(true,false,false,reason,innovationT,innovationR);reject(reason)};return decisions
        }
        if(lastAcceptedNs!=0L&&sample.timestampNs-lastAcceptedNs>500_000_000L){confirmations=0;pending=null;state=State.CANDIDATE;relocks++}
        val prior=pending
        val consistent=prior==null||((proposed.t-prior.t).norm()<=settings.innovationTranslationMm/1000&&Math.toDegrees(proposed.q.angle(prior.q))<=settings.innovationRotationDeg)
        if(consistent){confirmations++;pending=if(prior==null)proposed else prior.interpolate(proposed,1.0/confirmations)}else{confirmations=1;pending=proposed}
        lastAcceptedNs=sample.timestampNs;confidence=(inliers.sumOf{it.observation.confidence*it.weight}/inliers.sumOf{it.weight}).coerceIn(0.0,1.0)
        if(confirmations<settings.relockConfirmationFrames){state=State.CANDIDATE;for(c in inliers)decisions[c.observation.id]=MarkerUseDecision(true,true,false,MarkerRejectionReason.AWAITING_CONFIRMATION,innovationT,innovationR);return decisions}
        state=State.CONFIRMED
        val candidate=pending!!;val deadT=(candidate.t-correction.t).norm()<settings.translationDeadZoneMm/1000
        val deadR=Math.toDegrees(candidate.q.angle(correction.q))<settings.rotationDeadZoneDeg
        if(!(deadT&&deadR)){target=Rigid(if(deadT)correction.t else candidate.t,if(deadR)correction.q else candidate.q);blendStart=correction;blendStartNs=sample.timestampNs;state=State.BLENDING;corrections++}
        else state=State.LOCKED
        usedObservations+=inliers.size
        for(c in inliers)decisions[c.observation.id]=MarkerUseDecision(true,true,true,MarkerRejectionReason.NONE,innovationT,innovationR)
        confirmations=0;pending=null
        return decisions
    }
    @Synchronized fun apply(sample:ArPoseSample,strength:Double,settings:DetectorSettings?=null):Rigid? {
        lastApplyNs=sample.timestampNs
        if(sample.valid&&active&&state==State.BLENDING){
            // These are explicit perceptual transition durations, not recursive filter gains.
            // Keep the legacy strength argument for source compatibility with old callers.
            val translationMs=(settings?.relockTranslationBlendMs?:300).toDouble()
            val rotationMs=(settings?.relockRotationBlendMs?:400).toDouble()
            fun smooth(ms:Double):Double {val x=((sample.timestampNs-blendStartNs)/1e6/ms).coerceIn(0.0,1.0);return x*x*(3-2*x)}
            val at=smooth(translationMs);val ar=smooth(rotationMs);correction=Rigid(blendStart.t.lerp(target.t,at),blendStart.q.slerp(target.q,ar))
            if(at>=1&&ar>=1)state=State.LOCKED
        }
        return sample.relative?.let{correction*it}
    }
}

class MarkerMapCalibrator {
    private val observations=mutableMapOf<Int,MutableList<RobustPose.Weighted>>()
    private val sizes=mutableMapOf<Int,Double>()
    @Synchronized fun clear(){observations.clear();sizes.clear()}
    @Synchronized fun counts()=observations.mapValues{it.value.size}
    @Synchronized fun add(sample:ArPoseSample,markers:List<MarkerObservation>,settings:DetectorSettings){
        if(!sample.valid)return
        for(o in markers){if(o.confidence<settings.minConfidence||o.reprojectionError>settings.maxError||o.pixelArea<settings.minPixelArea||o.viewAngle>75)continue
            val list=observations.getOrPut(o.id){mutableListOf()};val pose=sample.relative!!*o.T_camera_marker
            if(list.size>=10){val center=RobustPose.estimate(list)?:continue;if((pose.t-center.t).norm()>.08||pose.q.angle(center.q)>Math.toRadians(10.0))continue}
            if(list.size<100){list+=RobustPose.Weighted(pose,o.confidence);sizes[o.id]=o.sizeM}
        }
    }
    @Synchronized fun build(name:String,settings:DetectorSettings,existing:MarkerMap):MarkerMap {
        val added=observations.filterValues{it.size>=30}.mapNotNull{(id,list)->RobustPose.estimate(list)?.let{center->
            val translationStd=sqrt(list.sumOf{(it.pose.t-center.t).let{d->d.dot(d)}}/list.size)
            val rotationStd=sqrt(list.sumOf{Math.toDegrees(it.pose.q.angle(center.q)).let{d->d*d}}/list.size)
            MarkerReference(id,sizes.getValue(id),center,list.size,translationStd,rotationStd,confidence=list.map{it.weight}.average().coerceIn(0.0,1.0))
        }}
        require(added.isNotEmpty()){ "Need at least 30 good observations per marker" }
        return MarkerMap(name,settings.dictionary,existing.references.filter{old->added.none{it.id==old.id}}+added,markerSizesM=existing.markerSizesM)
    }
}
