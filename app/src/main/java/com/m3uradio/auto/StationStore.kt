package com.m3uradio.auto

import android.content.Context
import java.io.File

/** M3U metnini cihazda saklar; hem uygulama hem Android Auto servisi buradan okur. */
object StationStore {

    private const val FILE_NAME = "stations.m3u"

    @Volatile private var cachedStamp = -1L
    @Volatile private var cached: List<Station> = emptyList()

    fun save(context: Context, m3uText: String): List<Station> {
        File(context.filesDir, FILE_NAME).writeText(m3uText)
        return load(context)
    }

    @Synchronized
    fun load(context: Context): List<Station> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val stamp = file.lastModified() xor file.length()
        if (stamp != cachedStamp) {
            cached = M3uParser.parse(file.readText())
            cachedStamp = stamp
        }
        return cached
    }
}
