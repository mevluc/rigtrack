package com.rigtrack.export

import com.rigtrack.core.math.*
import java.io.*

object Csv {
    fun row(vararg values: Any?): String = values.joinToString(",") { v -> val s=v?.toString()?:""; if(s.any{it==','||it=='"'||it=='\n'||it=='\r'}) "\"${s.replace("\"","\"\"")}\"" else s }
    fun pose(p:Rigid)=listOf(p.t.x,p.t.y,p.t.z,p.q.x,p.q.y,p.q.z,p.q.w)
    fun parse(line:String):List<String> {
        val out= mutableListOf<String>(); val cell=StringBuilder(); var quoted=false; var i=0
        while(i<line.length) { val c=line[i]; if(c=='"') { if(quoted&&i+1<line.length&&line[i+1]=='"') {cell.append('"'); i++} else quoted=!quoted } else if(c==','&&!quoted) {out+=cell.toString();cell.setLength(0)} else cell.append(c);i++ }
        require(!quoted){"Unterminated CSV cell"};out+=cell.toString();return out
    }
    fun rigid(v:List<String>,start:Int)=Rigid(V3(v[start].toDouble(),v[start+1].toDouble(),v[start+2].toDouble()),Q(v[start+3].toDouble(),v[start+4].toDouble(),v[start+5].toDouble(),v[start+6].toDouble()).normalized())
}
