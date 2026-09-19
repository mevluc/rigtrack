package com.rigtrack.export

import com.google.gson.*
import java.io.File

object Recovery {
    /** Original partial directory is retained untouched for forensic/offline recovery. */
    fun recover(partial:File):File {
        require(partial.name.endsWith(".partial"))
        val recovered=File(partial.parentFile,partial.name.removeSuffix(".partial")+"_recovered_${System.currentTimeMillis()}")
        check(recovered.mkdirs())
        partial.listFiles()?.filter{it.isFile&&!it.name.endsWith(".tmp")}?.forEach{it.copyTo(File(recovered,it.name))}
        var discarded=0L
        for(name in listOf("film_camera_raw.csv","film_camera_fused.csv")) {
            val source=File(partial,name);require(source.exists()){ "Missing $name: recording stopped before initialization" }
            source.bufferedReader().use{r->File(recovered,name).bufferedWriter().use{w->val header=r.readLine()?:error("Missing CSV header");w.appendLine(header);val count=Csv.parse(header).size
                r.lineSequence().forEach{line->val row=runCatching{Csv.parse(line)}.getOrNull();if(row!=null&&row.size==count&&row[0].toLongOrNull()!=null)w.appendLine(line)else discarded++}
            }}
        }
        val file=File(recovered,"metadata.json");val metadata=JsonParser.parseString(file.readText()).asJsonObject
        val start=metadata["record_start_monotonic_ns"].asLong
        var endTime=0.0;File(recovered,"film_camera_raw.csv").bufferedReader().use{r->r.lineSequence().drop(1).forEach{endTime=maxOf(endTime,Csv.parse(it)[1].toDouble())}}
        metadata.addProperty("record_stop_monotonic_ns",start+(endTime*1e9).toLong());metadata.addProperty("duration_s",endTime);metadata.addProperty("recovered",true);metadata.addProperty("recovery_discarded_derived_rows",discarded)
        file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(metadata));TrackExporter.process(recovered);return recovered
    }
}
