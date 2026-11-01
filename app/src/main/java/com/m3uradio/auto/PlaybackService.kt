package com.m3uradio.auto

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Android Auto'nun bağlandığı medya servisi.
 * Ağaç: kök -> "Tüm istasyonlar" + (varsa) grup klasörleri -> istasyonlar.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT_ID, "M3U Radyo"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val stations = StationStore.load(this@PlaybackService)
            val items: List<MediaItem> = when {
                parentId == ROOT_ID -> {
                    val groups = stations.mapNotNull { it.group }.distinct()
                    listOf(folder(ALL_ID, "Tüm istasyonlar")) +
                        groups.map { folder(GROUP_PREFIX + it, it) }
                }
                parentId == ALL_ID -> stations.map { it.toMediaItem() }
                parentId.startsWith(GROUP_PREFIX) -> {
                    val g = parentId.removePrefix(GROUP_PREFIX)
                    stations.filter { it.group == g }.map { it.toMediaItem() }
                }
                else -> return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            val size = if (pageSize > 0) pageSize else items.size
            val paged = items.drop(page * size).take(size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = findItem(mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        // Android Auto yalnızca mediaId gönderir; çalınabilir URI'yi burada dolduruyoruz.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { findItem(it.mediaId) ?: it }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid())
    }

    private fun findItem(mediaId: String): MediaItem? =
        StationStore.load(this).firstOrNull { it.id == mediaId }?.toMediaItem()

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()

    companion object {
        private const val ROOT_ID = "root"
        private const val ALL_ID = "all"
        private const val GROUP_PREFIX = "grp:"
    }
}
