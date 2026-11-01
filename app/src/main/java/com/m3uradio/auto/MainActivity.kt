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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var stations: List<Station> = emptyList()

    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var list: ListView

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
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
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
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val loadBtn = Button(this).apply {
            text = "Bağlantıdan yükle"
            setOnClickListener { loadFromUrl() }
        }
        val fileBtn = Button(this).apply {
            text = "Dosya seç"
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(i, REQ_FILE)
            }
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        buttons.addView(loadBtn, lp)
        buttons.addView(fileBtn, lp)

        status = TextView(this).apply {
            gravity = Gravity.START
            setPadding(0, pad / 2, 0, pad / 2)
        }
        list = ListView(this).apply {
            setOnItemClickListener { _, _, position, _ -> play(position) }
        }
        root.addView(urlInput)
        root.addView(buttons)
        root.addView(status)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
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
                val text = conn.inputStream.use { it.readBytes() }
                    .let { if (it.size > MAX_BYTES) throw IllegalStateException("Dosya çok büyük") else it }
                    .toString(Charsets.UTF_8)
                val result = StationStore.save(this, text)
                runOnUiThread { showStations(result) }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Yüklenemedi: ${e.message}"
                }
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
        val future = controllerFuture
        val controller = if (future != null && future.isDone) {
            try { future.get() } catch (e: Exception) { null }
        } else null
        if (controller == null) {
            toast("Oynatıcı hazırlanıyor, tekrar deneyin")
            return
        }
        controller.setMediaItems(stations.map { it.toMediaItem() }, position, 0L)
        controller.prepare()
        controller.play()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_FILE = 42
        private const val MAX_BYTES = 5 * 1024 * 1024
    }
}
