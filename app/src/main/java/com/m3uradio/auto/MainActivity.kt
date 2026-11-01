package com.m3uradio.auto

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var stations: List<Station> = emptyList()

    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var list: ListView
    private lateinit var prevBtn: Button
    private lateinit var playBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var muteBtn: Button

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshControls()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshControls()
        override fun onVolumeChanged(volume: Float) = refreshControls()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshControls()
        override fun onPlayerError(error: PlaybackException) {
            // Sessizlik nedeni görünsün diye hatayı ekranda yaz.
            nowPlaying.text = "Çalamadı: ${error.errorCodeName}" +
                (error.cause?.message?.let { " ($it)" } ?: "")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        urlInput.setText(getSharedPreferences("prefs", MODE_PRIVATE).getString("url", ""))
        showStations(StationStore.load(this))
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                refreshControls()
            } catch (e: Exception) {
                nowPlaying.text = "Oynatıcıya bağlanılamadı: ${e.message}"
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            fitsSystemWindows = true
        }
        urlInput = EditText(this).apply {
            hint = "M3U bağlantısı (https://...)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val equal = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val loadRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        loadRow.addView(
            Button(this).apply {
                text = "Bağlantıdan yükle"
                setOnClickListener { loadFromUrl() }
            }, equal,
        )
        loadRow.addView(
            Button(this).apply {
                text = "Dosya seç"
                setOnClickListener {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(i, REQ_FILE)
                }
            }, equal,
        )

        status = TextView(this).apply {
            gravity = Gravity.START
            setPadding(0, pad / 2, 0, 0)
        }
        nowPlaying = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 2)
            text = "Bir radyo seçin"
        }

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        prevBtn = Button(this).apply {
            text = "⏮ Geri"
            contentDescription = "Önceki radyo"
            setOnClickListener { controller?.seekToPreviousMediaItem() ?: notReady() }
        }
        playBtn = Button(this).apply {
            text = "▶ Oynat"
            setOnClickListener { togglePlay() }
        }
        nextBtn = Button(this).apply {
            text = "İleri ⏭"
            contentDescription = "Sonraki radyo"
            setOnClickListener { controller?.seekToNextMediaItem() ?: notReady() }
        }
        muteBtn = Button(this).apply {
            text = "🔇 Sessiz"
            setOnClickListener { toggleMute() }
        }
        controls.addView(prevBtn, equal)
        controls.addView(playBtn, equal)
        controls.addView(nextBtn, equal)
        controls.addView(muteBtn, equal)

        list = ListView(this).apply {
            setOnItemClickListener { _, _, position, _ -> play(position) }
        }
        root.addView(urlInput)
        root.addView(loadRow)
        root.addView(status)
        root.addView(nowPlaying)
        root.addView(controls)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun refreshControls() {
        val c = controller ?: return
        playBtn.text = if (c.isPlaying) "⏸ Duraklat" else "▶ Oynat"
        muteBtn.text = if (c.volume == 0f) "🔊 Sesi aç" else "🔇 Sessiz"
        val title = c.mediaMetadata.title
        if (c.playbackState == Player.STATE_BUFFERING) {
            nowPlaying.text = "Bağlanıyor: ${title ?: ""}"
        } else if (title != null) {
            nowPlaying.text = "Çalıyor: $title"
        }
    }

    private fun togglePlay() {
        val c = controller ?: return notReady()
        if (c.mediaItemCount == 0) {
            if (stations.isNotEmpty()) play(0) else toast("Önce bir liste yükleyin")
            return
        }
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    private fun toggleMute() {
        val c = controller ?: return notReady()
        c.volume = if (c.volume == 0f) 1f else 0f
        refreshControls()
    }

    private fun loadFromUrl() {
        val url = urlInput.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Geçerli bir http/https bağlantısı girin")
            return
        }
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("url", url).apply()
        status.text = "Yükleniyor..."
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.size > MAX_BYTES) throw IllegalStateException("Dosya çok büyük")
                val result = StationStore.save(this, bytes.toString(Charsets.UTF_8))
                runOnUiThread { showStations(result) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Yüklenemedi: ${e.message}" }
            }
        }
    }

    @Deprecated("Basit tutmak için eski API kullanılıyor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        thread {
            try {
                val text = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                val result = StationStore.save(this, text)
                runOnUiThread { showStations(result) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Dosya okunamadı: ${e.message}" }
            }
        }
    }

    private fun showStations(result: List<Station>) {
        stations = result
        status.text = if (result.isEmpty()) "Henüz istasyon yok. M3U yükleyin."
        else "${result.size} istasyon yüklendi. Android Auto'da da görünür."
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            result.map { s -> if (s.group != null) "${s.name}  ·  ${s.group}" else s.name },
        )
    }

    private fun play(position: Int) {
        val c = controller ?: return notReady()
        c.volume = 1f
        c.setMediaItems(stations.map { it.toMediaItem() }, position, 0L)
        c.prepare()
        c.play()
    }

    private fun notReady() = toast("Oynatıcı hazırlanıyor, tekrar deneyin")

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_FILE = 42
        private const val MAX_BYTES = 5 * 1024 * 1024
    }
}
