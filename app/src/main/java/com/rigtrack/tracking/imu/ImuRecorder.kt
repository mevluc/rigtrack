package com.rigtrack.tracking.imu

import android.content.Context
import android.hardware.*
import android.os.*
import com.rigtrack.core.model.ImuSample
import java.util.concurrent.ConcurrentHashMap

class SampleRate {
    private var first=0L;private var last=0L;private var count=0L
    @Synchronized fun add(ns:Long){if(ns<=last)return;if(first==0L)first=ns;last=ns;count++}
    @Synchronized fun hz()=if(last>first)(count-1)*1e9/(last-first)else 0.0
}

class ImuRecorder(context:Context,private val consumer:(ImuSample)->Unit):SensorEventListener {
    private val manager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var thread:HandlerThread?=null
    val rates=ConcurrentHashMap<Int,SampleRate>()
    val sensors:List<Sensor> = (listOf(Sensor.TYPE_GYROSCOPE,Sensor.TYPE_GYROSCOPE_UNCALIBRATED,Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_ROTATION_VECTOR)+if(Build.VERSION.SDK_INT>=26)listOf(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)else emptyList()).mapNotNull{manager.getDefaultSensor(it)}
    val registrationErrors=mutableListOf<String>()
    fun start(periodUs:Int=0){if(thread!=null)return;val t=HandlerThread("RigTrack-IMU",Process.THREAD_PRIORITY_MORE_FAVORABLE);t.start();thread=t;val handler=Handler(t.looper)
        for(s in sensors){rates[s.type]=SampleRate();try{if(!manager.registerListener(this,s,periodUs,0,handler))registrationErrors+="${s.name}: unavailable"}catch(e:SecurityException){registrationErrors+="${s.name}: high-rate permission limited";manager.registerListener(this,s,5000,0,handler)}}
    }
    fun stop(){manager.unregisterListener(this);thread?.quitSafely();thread=null}
    override fun onSensorChanged(event:SensorEvent){rates[event.sensor.type]?.add(event.timestamp);consumer(ImuSample(event.timestamp,SystemClock.elapsedRealtimeNanos(),event.sensor.type,event.values.toList(),event.accuracy))}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    fun metadata()=sensors.map{mapOf("type" to it.type,"name" to it.name,"vendor" to it.vendor,"version" to it.version,"min_delay_us" to it.minDelay,"resolution" to it.resolution,"max_range" to it.maximumRange)}
}
