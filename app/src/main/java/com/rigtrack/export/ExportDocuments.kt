package com.rigtrack.export

import com.google.gson.JsonParser
import java.io.File

data class ExportDocumentSpec(val mimeType:String,val displayName:String)

/** Storage Access Framework contract. The payload is ZIP, but its user-facing type is .vfxtrack. */
object ExportDocuments {
    const val VFXTRACK_MIME="application/vnd.rigtrack.vfxtrack"

    fun spec(source:File,kind:String):ExportDocumentSpec {
        val sourceName=if(source.isDirectory)runCatching {
            JsonParser.parseString(File(source,"metadata.json").readText()).asJsonObject
                .getAsJsonObject("shot")["name"].asString.trim().ifBlank { source.name }
        }.getOrDefault(source.name)else source.name
        return spec(sourceName,kind)
    }

    fun spec(sourceName:String,kind:String):ExportDocumentSpec=when(kind){
        "csv"->ExportDocumentSpec("text/csv","${sourceName}_blender_camera.csv")
        "pdf"->ExportDocumentSpec("application/pdf",sourceName)
        else->ExportDocumentSpec(VFXTRACK_MIME,vfxtrackName(sourceName))
    }

    fun vfxtrackName(sourceName:String):String {
        var base=sourceName
        if(base.endsWith(".zip",ignoreCase=true))base=base.dropLast(4)
        if(base.endsWith(".vfxtrack",ignoreCase=true))base=base.dropLast(9)
        return "$base.vfxtrack"
    }
}
