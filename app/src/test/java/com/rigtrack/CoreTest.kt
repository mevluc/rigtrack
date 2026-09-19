package com.rigtrack

import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import com.rigtrack.export.*
import org.junit.Test
import org.junit.Assert.*

class CoreTest {
    private fun near(a:V3,b:V3){assertTrue("$a != $b",(a-b).norm()<1e-8)}
    @Test fun matrixInverse(){val t=Rigid(V3(1.0,2.0,3.0),Q.eulerXYZ(.4,.7,-.9));near((t*t.inverse()).t,V3());assertTrue((t.inverse()*t).q.angle(Q())<1e-8);assertTrue(Rigid.fromMatrix(t.matrix()).q.angle(t.q)<1e-7)}
    @Test fun anchorRelative(){val anchor=Rigid(V3(1.0,2.0,3.0),Q.eulerXYZ(.4,.7,0.0));val local=Rigid(V3(0.0,0.0,-1.0));near((anchor.inverse()*(anchor*local)).t,local.t)}
    @Test fun leverArm(){val phone=Rigid(q=Q.axis(V3(0.0,0.0,1.0),Math.PI/2));val rig=Rigid(V3(0.0,-.1,0.0));near((phone*rig).t,V3(.1,0.0,0.0))}
    @Test fun blenderBasis(){near(CoordinateConversion.toBlenderCamera(Rigid(V3(0.0,0.0,-1.0))).t,V3(0.0,1.0,0.0));near(CoordinateConversion.toBlenderCamera(Rigid()).q.rotate(V3(0.0,0.0,-1.0)),V3(0.0,1.0,0.0))}
    @Test fun slerp(){val q=Q.axis(V3(0.0,1.0,0.0),Math.PI);near(Q().slerp(q,.5).rotate(V3(0.0,0.0,-1.0)),V3(-1.0,0.0,0.0));assertTrue(q.slerp(-q,.5).angle(q)<1e-8)}
    @Test fun markerDirection(){val camera=Rigid(V3(2.0,0.0,1.0),Q.eulerXYZ(.1,.3,0.0));val marker=Rigid(V3(1.0,1.0,-4.0));val cameraMarker=camera.inverse()*marker;near((marker*cameraMarker.inverse()).t,camera.t)}
    @Test fun rationalResampling(){for(fps in listOf(Fps(24000,1001),Fps(25),Fps(60))){val source=(0..180).map{TimedPose(it*16_666_667L,Rigid(V3(it/60.0,0.0,0.0)))};val rows=Resampler.frames(source.asSequence(),fps,0,3_000_000_000,100_000_000).toList();assertEquals(1L,rows.first().index);assertEquals(fps.seconds(1),rows[1].timeS,1e-12);assertEquals(rows[1].timeS,rows[1].pose!!.t.x,1e-7)}}
    @Test fun csvLocale(){val old=java.util.Locale.getDefault();try{java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));assertEquals("1.25,\"a,b\",\"a\"\"b\"",Csv.row(1.25,"a,b","a\"b"));assertEquals(listOf("1.25","a,b","a\"b"),Csv.parse(Csv.row(1.25,"a,b","a\"b")))}finally{java.util.Locale.setDefault(old)}}
}
