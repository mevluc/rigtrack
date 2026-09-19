package com.rigtrack.export

import com.rigtrack.core.math.*
import com.rigtrack.core.model.TimedPose
import java.io.File
import kotlin.math.*

/** Offline-only refinement. Input files are never modified and timestamps are preserved. */
object TrackRefiner {
    data class Config(val radius:Int,val strength:Double,val outlierSigma:Double,val translationFloorM:Double,val rotationFloorDeg:Double) {
        companion object { fun from(name:String)=when(name.lowercase()) {
            "off" -> Config(0,0.0,8.0,.05,3.0); "medium" -> Config(3,.50,6.0,.03,2.0); "high" -> Config(4,.68,5.0,.02,1.5); else -> Config(2,.32,7.0,.04,2.5)
        } }
    }
    data class Sample(val timestampNs:Long,val pose:Rigid?,val quality:Double,val originalValid:Boolean,
        val repairedGap:Boolean=false,val outlierRejected:Boolean=false)
    data class Metrics(val inputSamples:Int,val validInputSamples:Int,val rejectedOutliers:Int,val repairedSamples:Int,
        val repairedGaps:Int,val longestRepairedGapMs:Double,val unrepairedGaps:Int,val longestUnrepairedGapMs:Double,val processingMs:Double,val inputTranslationJitterMm:Double,
        val refinedTranslationJitterMm:Double,val inputRotationJitterDeg:Double,val refinedRotationJitterDeg:Double,
        val maximumTranslationDeviationMm:Double,val maximumRotationDeviationDeg:Double)
    data class Result(val samples:List<Sample>,val metrics:Metrics)

    private fun median(values:List<Double>):Double = if(values.isEmpty())0.0 else values.sorted().let { v ->
        if(v.size%2==0)(v[v.size/2-1]+v[v.size/2])/2 else v[v.size/2]
    }
    private data class Residual(val translation:Double,val rotation:Double)
    private fun residual(a:Sample,b:Sample,c:Sample):Residual? {
        val ap=a.pose?:return null;val bp=b.pose?:return null;val cp=c.pose?:return null
        val span=c.timestampNs-a.timestampNs;if(span<=0)return null
        val f=(b.timestampNs-a.timestampNs).toDouble()/span
        val predicted=ap.interpolate(cp,f)
        return Residual((bp.t-predicted.t).norm(),Math.toDegrees(bp.q.angle(predicted.q)))
    }
    private fun jitter(samples:List<Sample>):Pair<Double,Double> {
        val r=(1 until samples.lastIndex).mapNotNull{i->residual(samples[i-1],samples[i],samples[i+1])}
        if(r.isEmpty())return 0.0 to 0.0
        return sqrt(r.sumOf{it.translation*it.translation}/r.size)*1000 to sqrt(r.sumOf{it.rotation*it.rotation}/r.size)
    }
    private fun quaternionMean(items:List<Pair<Q,Double>>):Q {
        val reference=items.first().first;var x=0.0;var y=0.0;var z=0.0;var w=0.0
        for((source,weight) in items){val q=if(source.dot(reference)<0)-source else source;x+=q.x*weight;y+=q.y*weight;z+=q.z*weight;w+=q.w*weight}
        return Q(x,y,z,w).normalized()
    }

    fun refine(input:List<TimedPose>,preset:String="Low",maxGapNs:Long=100_000_000L):Result {
        val started=System.nanoTime();val config=Config.from(preset)
        val original=input.map{Sample(it.timestampNs,it.pose.takeIf{_->it.valid},it.quality,it.valid)}
        if(original.isEmpty())return Result(emptyList(),Metrics(0,0,0,0,0,0.0,0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0))

        // A local constant-velocity prediction rejects isolated spikes without clipping a fast pan.
        val residuals=(1 until original.lastIndex).mapNotNull{i->residual(original[i-1],original[i],original[i+1])}
        val tMedian=median(residuals.map{it.translation});val rMedian=median(residuals.map{it.rotation})
        val tMad=median(residuals.map{abs(it.translation-tMedian)})*1.4826
        val rMad=median(residuals.map{abs(it.rotation-rMedian)})*1.4826
        val tGate=max(config.translationFloorM,tMedian+config.outlierSigma*tMad);val rGate=max(config.rotationFloorDeg,rMedian+config.outlierSigma*rMad)
        val working=original.toMutableList();var rejected=0
        for(i in 1 until original.lastIndex){val rr=residual(original[i-1],original[i],original[i+1])?:continue
            val before=if(i>1)residual(original[i-2],original[i-1],original[i]) else null
            val after=if(i+2<original.size)residual(original[i],original[i+1],original[i+2]) else null
            val peakT=rr.translation>=max(before?.translation?:0.0,after?.translation?:0.0)*1.5
            val peakR=rr.rotation>=max(before?.rotation?:0.0,after?.rotation?:0.0)*1.5
            if((rr.translation>tGate&&peakT)||(rr.rotation>rGate&&peakR)){working[i]=working[i].copy(pose=null,outlierRejected=true);rejected++}
        }

        // Repair only bounded internal gaps. No leading/trailing extrapolation and no long-gap bridge.
        var repairedSamples=0;var repairedGaps=0;var longestGap=0L;var unrepairedGaps=0;var longestUnrepaired=0L
        for(j in 1 until original.size){val duration=original[j].timestampNs-original[j-1].timestampNs
            if(original[j-1].pose!=null&&original[j].pose!=null&&duration>maxGapNs){unrepairedGaps++;longestUnrepaired=max(longestUnrepaired,duration)}
        }
        var i=0
        while(i<working.size){if(working[i].pose!=null){i++;continue};val begin=i;while(i<working.size&&working[i].pose==null)i++;val end=i-1
            val left=begin-1;val right=i
            if(left>=0&&right<working.size){val duration=working[right].timestampNs-working[left].timestampNs
                if(duration in 1..maxGapNs){val a=working[left].pose!!;val b=working[right].pose!!
                    for(j in begin..end){val f=(working[j].timestampNs-working[left].timestampNs).toDouble()/duration;working[j]=working[j].copy(pose=a.interpolate(b,f),quality=min(working[left].quality,working[right].quality)*.5,repairedGap=true);repairedSamples++}
                    repairedGaps++;longestGap=max(longestGap,duration)
                } else {unrepairedGaps++;longestUnrepaired=max(longestUnrepaired,duration)}
            }
        }

        // Centered, symmetric kernel: linear/constant-speed movement retains its timing and amplitude.
        val refined=working.mapIndexed { index,s ->
            val pose=s.pose
            if(pose==null||config.radius==0||index<config.radius||index+config.radius>working.lastIndex)s else {
                val members=(max(0,index-config.radius)..min(working.lastIndex,index+config.radius)).mapNotNull { j ->
                    working[j].pose?.takeIf{abs(working[j].timestampNs-s.timestampNs)<=maxGapNs}?.let{Triple(it,(config.radius+1-abs(j-index)).toDouble(),j)}
                }
                if(members.size<2)s else {
                    val total=members.sumOf{it.second};var meanT=V3();for((p,w,_) in members)meanT=meanT+p.t*(w/total)
                    val meanQ=quaternionMean(members.map{it.first.q to it.second})
                    // Reduce attenuation at high velocity; the symmetric kernel still removes frame jitter.
                    val previous=working.getOrNull(index-1)?.pose;val next=working.getOrNull(index+1)?.pose
                    val dt=((working.getOrNull(index+1)?.timestampNs?:s.timestampNs)-(working.getOrNull(index-1)?.timestampNs?:s.timestampNs))/1e9
                    val linearSpeed=if(previous!=null&&next!=null&&dt>0)(next.t-previous.t).norm()/dt else 0.0
                    val angularSpeed=if(previous!=null&&next!=null&&dt>0)Math.toDegrees(previous.q.angle(next.q))/dt else 0.0
                    val adaptive=config.strength/(1+linearSpeed*.08+angularSpeed/720.0)
                    s.copy(pose=Rigid(pose.t.lerp(meanT,adaptive),pose.q.slerp(meanQ,adaptive)))
                }
            }
        }
        val inputJitter=jitter(original);val refinedJitter=jitter(refined)
        var maxT=0.0;var maxR=0.0
        for(index in original.indices){val a=original[index].pose;val b=refined[index].pose;if(a!=null&&b!=null){maxT=max(maxT,(a.t-b.t).norm()*1000);maxR=max(maxR,Math.toDegrees(a.q.angle(b.q)))}}
        return Result(refined,Metrics(input.size,original.count{it.pose!=null},rejected,repairedSamples,repairedGaps,longestGap/1e6,unrepairedGaps,longestUnrepaired/1e6,
            (System.nanoTime()-started)/1e6,inputJitter.first,refinedJitter.first,inputJitter.second,refinedJitter.second,maxT,maxR))
    }

    fun read(file:File):List<TimedPose> = file.bufferedReader().use { r -> r.lineSequence().drop(1).filter{it.isNotBlank()}.map(Csv::parse).map { v ->
        TimedPose(v[0].toLong(),if(v[2].isNotEmpty())Csv.rigid(v,2)else Rigid(),v[10].toBoolean(),v[9].toDouble())
    }.toList() }
    fun readSamples(file:File):List<Sample> = file.bufferedReader().use {r->r.lineSequence().drop(1).filter{it.isNotBlank()}.map(Csv::parse).map{v->
        Sample(v[0].toLong(),if(v[2].isNotEmpty())Csv.rigid(v,2)else null,v[9].toDouble(),v[10].toBoolean(),v.getOrNull(11)?.toBoolean()?:false,v.getOrNull(12)?.toBoolean()?:false)
    }.toList()}

    fun evaluate(input:List<TimedPose>,output:List<Sample>,base:Metrics):Metrics {
        val source=input.map{Sample(it.timestampNs,it.pose.takeIf{_->it.valid},it.quality,it.valid)};val a=jitter(source);val b=jitter(output)
        var maxT=0.0;var maxR=0.0
        for(i in source.indices){val x=source[i].pose;val y=output.getOrNull(i)?.pose;if(x!=null&&y!=null){maxT=max(maxT,(x.t-y.t).norm()*1000);maxR=max(maxR,Math.toDegrees(x.q.angle(y.q)))}}
        return base.copy(inputSamples=input.size,validInputSamples=source.count{it.pose!=null},inputTranslationJitterMm=a.first,refinedTranslationJitterMm=b.first,
            inputRotationJitterDeg=a.second,refinedRotationJitterDeg=b.second,maximumTranslationDeviationMm=maxT,maximumRotationDeviationDeg=maxR)
    }

    fun write(input:File,output:File,preset:String,maxGapNs:Long):Metrics {
        val sourceRows=input.bufferedReader().use{r->r.lineSequence().drop(1).filter{it.isNotBlank()}.map(Csv::parse).toList()}
        val timed=sourceRows.map{v->TimedPose(v[0].toLong(),if(v[2].isNotEmpty())Csv.rigid(v,2)else Rigid(),v[10].toBoolean(),v[9].toDouble())}
        val result=refine(timed,preset,maxGapNs)
        output.bufferedWriter().use { w ->
            w.appendLine("timestamp_ns,time_s,tx,ty,tz,qx,qy,qz,qw,quality,valid,repaired_gap,outlier_rejected")
            for((index,s) in result.samples.withIndex())w.appendLine(Csv.row(*(listOf(s.timestampNs,sourceRows[index][1])+(s.pose?.let(Csv::pose)?:List<Any?>(7){null})+listOf(s.quality,s.pose!=null,s.repairedGap,s.outlierRejected)).toTypedArray()))
        }
        return result.metrics
    }
}
