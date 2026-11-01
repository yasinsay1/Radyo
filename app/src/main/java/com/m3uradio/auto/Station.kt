package com.m3uradio.auto

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
) {
    fun toMediaItem(): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(group)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        if (!logo.isNullOrBlank()) meta.setArtworkUri(Uri.parse(logo))
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMediaMetadata(meta.build())
            .build()
    }
}
