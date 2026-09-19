package com.rigtrack.data

import com.rigtrack.export.Csv
import java.io.File

/** UI-only summary of recorded scores. Does not modify the tracking streams. */
object RecordingQuality {
    fun average(directory: File): Int? {
        if (directory.name.endsWith(".partial")) return null
        val source = File(directory, "film_camera_raw.csv")
        if (!source.exists()) return null
        return runCatching {
            var sum = 0.0; var count = 0L
            source.bufferedReader().use { reader ->
                val index = Csv.parse(reader.readLine() ?: return null).indexOf("quality")
                if (index < 0) return null
                reader.lineSequence().forEach { line ->
                    val quality = Csv.parse(line).getOrNull(index)?.toDoubleOrNull()
                    if (quality != null && quality.isFinite()) { sum += quality.coerceIn(0.0, 100.0); count++ }
                }
            }
            if (count > 0) (sum / count).toInt() else null
        }.getOrNull()
    }
}
