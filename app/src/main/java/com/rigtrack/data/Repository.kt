package com.rigtrack.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.rigtrack.core.model.*
import java.io.File

class Repository(context:Context) {
    val sessions=File(context.filesDir,"sessions").apply{mkdirs()}
    private val profiles=File(context.filesDir,"profiles").apply{mkdirs()}
    private val gson=GsonBuilder().setPrettyPrinting().create()
    @Synchronized fun save(name:String,value:Any){val file=AtomicFile(File(profiles,name));val stream=file.startWrite();try{stream.write(gson.toJson(value).toByteArray(Charsets.UTF_8));file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}}
    fun <T> load(name:String,type:Class<T>):T?=runCatching{gson.fromJson(AtomicFile(File(profiles,name)).readFully().toString(Charsets.UTF_8),type)}.getOrNull()
    @Synchronized fun settings():DetectorSettings {
        val stored=load("settings.json",DetectorSettings::class.java)?:DetectorSettings()
        val migration=File(profiles,"marker_dictionary_v2.json")
        if(migration.exists())return stored
        // 1.1 and earlier persisted ArUco 4x4/50 as an implicit default while the generator
        // opened on AprilTag 36h11. Preserve explicit/legacy ArUco maps; only migrate an
        // unbound default setting, then mark it so a later user choice of dictionary 0 sticks.
        val migrated=if(stored.dictionary==0&&maps().isEmpty())stored.copy(dictionary=MarkerDictionaries.DEFAULT)else stored
        if(migrated!=stored)save("settings.json",migrated)
        save("marker_dictionary_v2.json",mapOf("completed" to true,"from" to stored.dictionary,"to" to migrated.dictionary))
        return migrated
    }
    fun rigs()=load("rigs.json",Array<RigProfile>::class.java)?.toList()?:listOf(RigProfile())
    fun maps()=load("maps.json",Array<MarkerMap>::class.java)?.toList()?:emptyList()
    fun calibrations()=load("calibrations.json",Array<CalibrationProfile>::class.java)?.toList()?:emptyList()
    fun films()=load("films.json",Array<FilmSettings>::class.java)?.toList()?:listOf(FilmSettings())
    fun recordings()=sessions.listFiles()?.filter{it.isDirectory}?.sortedByDescending{it.name}?:emptyList()
}
