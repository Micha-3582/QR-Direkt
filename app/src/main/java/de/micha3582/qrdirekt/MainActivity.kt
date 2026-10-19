package de.micha3582.qrdirekt

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_RESULT = "result"
        private const val REQ_CAMERA = 10
    }

    private val pickMode by lazy { intent.getBooleanExtra(EXTRA_PICK, false) }
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    @Volatile private var handled = false
    private var provider: ProcessCameraProvider? = null
    private var prevUse: Preview? = null
    private var analysisUse: ImageAnalysis? = null
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var torchOn = false
    private var hintView: TextView? = null
    private var frameView: View? = null
    private var previewView: PreviewView? = null
    private val frameBg = GradientDrawable()
    private var torchView: View? = null
    private val torchBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x88000000.toInt()) }
    private lateinit var root: FrameLayout

    private fun t(de: String, en: String) = Lang.t(this, de, en)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera() else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != REQ_CAMERA) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera() else showDenied()
    }

    private fun showDenied() {
        root.removeAllViews()
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        box.addView(TextView(this).apply {
            text = t("QR Direkt braucht die Kamera, um QR-Codes zu lesen.", "QR Direkt needs the camera to read QR codes.")
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })
        box.addView(Button(this).apply {
            text = t("App-Einstellungen öffnen", "Open app settings")
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
                finish()
            }
        })
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
        addGear()
    }

    private fun startCamera() {
        val preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        previewView = preview
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val frame = View(this).apply {
            background = frameBg.apply {
                setStroke(dp(3), Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
        }
        frameView = frame
        root.addView(frame, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))

        hintView = TextView(this).apply {
            text = t("QR-Code in den Rahmen halten", "Hold the QR code inside the frame")
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(0x88000000.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        root.addView(hintView, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(48)
        })
        addGear()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            provider = future.get()
            prevUse = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            analysisUse = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { a ->
                    a.setAnalyzer(analysisExecutor) { proxy ->
                        val media = proxy.image
                        if (media == null || handled) { proxy.close(); return@setAnalyzer }
                        val rot = proxy.imageInfo.rotationDegrees
                        // Bildgröße nach Drehung (so liegt auch die erkannte Box vor)
                        val imgW = if (rot % 180 == 0) proxy.width else proxy.height
                        val imgH = if (rot % 180 == 0) proxy.height else proxy.width
                        val image = InputImage.fromMediaImage(media, rot)
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                val code = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                                val value = code?.rawValue
                                val box = code?.boundingBox
                                runOnUiThread { onFrame(value, box, imgW, imgH) }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Bindet Vorschau und Analyse (neu). Wird auch in onResume aufgerufen: Eine zweite
     * Instanz (Domain per QR hinzufügen) darf die Kamera dieser Instanz nicht dauerhaft kappen.
     */
    private fun bindCamera() {
        val prov = provider ?: return
        val prev = prevUse ?: return
        val analysis = analysisUse ?: return
        try {
            prov.unbind(prev, analysis)
            val camera = prov.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, analysis)
            cameraControl = camera.cameraControl
            setTorch(false)
            if (camera.cameraInfo.hasFlashUnit()) showTorchButton()
        } catch (e: Exception) {
            Toast.makeText(this, t("Kamera nicht verfügbar: ${e.message}", "Camera unavailable: ${e.message}"), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Sprache kann in den Einstellungen gewechselt worden sein
        hintView?.text = t("QR-Code in den Rahmen halten", "Hold the QR code inside the frame")
        torchView?.contentDescription = t("Taschenlampe", "Flashlight")
        if (!handled) bindCamera()
    }

    // --- Lock-on: Rahmen folgt dem Code, nach kurzer stabiler Erkennung wird die Aktion ausgeführt ---
    private val lockMs = 600L
    private var lockedValue: String? = null
    private var lockStart = 0L
    private var ignoredValue: String? = null
    private var ignoredSeen = 0L
    private var lastValue: String? = null
    private var cur: FloatArray? = null // aktuelle Rahmenposition l, t, w, h

    private val resetFrame = Runnable {
        if (!handled) {
            lockedValue = null
            cur = null
            val f = frameView ?: return@Runnable
            val lp = f.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.CENTER
            lp.leftMargin = 0; lp.topMargin = 0
            lp.width = dp(250); lp.height = dp(250)
            f.layoutParams = lp
            frameBg.setStroke(dp(3), Color.WHITE)
        }
    }

    private fun onFrame(value: String?, box: android.graphics.Rect?, imgW: Int, imgH: Int) {
        if (handled || isFinishing) return
        val now = android.os.SystemClock.uptimeMillis()
        if (ignoredValue != null && (value == null || value != ignoredValue) && now - ignoredSeen > 800) ignoredValue = null
        if (value == null || box == null) return
        if (value == ignoredValue) { ignoredSeen = now; return }
        val frame = frameView ?: return
        val pv = previewView ?: return
        if (pv.width == 0 || imgW == 0 || imgH == 0) return

        if (value != lockedValue) { lockedValue = value; lockStart = now }

        // Position der erkannten Box auf die Vorschau umrechnen (PreviewView: FILL_CENTER)
        val scale = maxOf(pv.width.toFloat() / imgW, pv.height.toFloat() / imgH)
        val offX = (imgW * scale - pv.width) / 2f
        val offY = (imgH * scale - pv.height) / 2f
        val pad = dp(14)
        val target = floatArrayOf(
            box.left * scale - offX - pad, box.top * scale - offY - pad,
            box.width() * scale + 2 * pad, box.height() * scale + 2 * pad)
        val c = cur ?: floatArrayOf(frame.left.toFloat(), frame.top.toFloat(), frame.width.toFloat(), frame.height.toFloat())
        for (i in 0..3) c[i] += (target[i] - c[i]) * 0.6f // weich nachführen
        cur = c

        val lp = frame.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = c[0].toInt(); lp.topMargin = c[1].toInt()
        lp.width = c[2].toInt(); lp.height = c[3].toInt()
        frame.layoutParams = lp
        frameBg.setStroke(dp(4), 0xFF4CAF50.toInt())

        // Verschwindet der Code, geht der Rahmen zurück in die Mitte
        frame.removeCallbacks(resetFrame)
        frame.postDelayed(resetFrame, 450)

        if (now - lockStart >= lockMs) {
            handled = true
            lastValue = value
            frame.removeCallbacks(resetFrame)
            frame.animate().alpha(0f).setDuration(350).start() // Rahmen sanft ausblenden
            onScanned(value)
        }
    }

    /** Nach Abbrechen/Schließen: weiter scannen, derselbe Code löst erst nach Wegnehmen wieder aus. */
    private fun resumeScanning() {
        if (isFinishing) return
        ignoredValue = lastValue
        ignoredSeen = android.os.SystemClock.uptimeMillis()
        lockedValue = null
        handled = false
        frameView?.let {
            it.removeCallbacks(resetFrame)
            it.animate().cancel()
            resetFrame.run()
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(250).start() // Rahmen blendet wieder ein
        }
    }

    private fun setTorch(on: Boolean) {
        torchOn = on
        cameraControl?.enableTorch(on)
        torchBg.setColor(if (on) 0xCCFFC107.toInt() else 0x88000000.toInt())
    }

    /** Taschenlampe oben links: bei schlechtem Licht ein-/ausschalten. */
    private fun showTorchButton() {
        if (torchView != null) return
        val torch = TextView(this).apply {
            text = "🔦"
            textSize = 24f
            gravity = Gravity.CENTER
            background = torchBg
            contentDescription = t("Taschenlampe", "Flashlight")
            setOnClickListener { setTorch(!torchOn) }
        }
        torchView = torch
        root.addView(torch, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(16); marginStart = dp(16)
        })
    }

    /** Zahnrad oben rechts (nicht im Auswahlmodus der Einstellungen). */
    private fun addGear() {
        if (pickMode) return
        val gear = TextView(this).apply {
            text = "⚙"
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x88000000.toInt())
            }
            contentDescription = t("Einstellungen", "Settings")
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        root.addView(gear, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(16); marginEnd = dp(16)
        })
    }

    private fun onScanned(value: String) {
        if (pickMode) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, value))
            finish()
        } else openUrl(value)
    }

    private fun openUrl(value: String?) {
        val url = value?.trim().orEmpty()
        if (!url.startsWith("https://", ignoreCase = true)) {
            showText(value.orEmpty())
            return
        }
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        if (Trusted.isTrusted(this, host)) launch(url) else confirmOpen(url, host)
    }

    private fun launch(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, t("Kein Browser gefunden", "No browser found"), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun confirmOpen(url: String, host: String) {
        val tv = TextView(this).apply {
            text = "Domain: $host\n\n$url"
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(60, 40, 60, 20)
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(t("Diese Seite öffnen?", "Open this page?"))
            .setView(tv)
            .setPositiveButton(t("Öffnen", "Open")) { _, _ -> launch(url) }
            .setNeutralButton(t("Immer öffnen", "Always open")) { _, _ ->
                Trusted.add(this, host)
                Toast.makeText(this, t("Domain eingetragen: $host", "Domain added: $host"), Toast.LENGTH_SHORT).show()
                launch(url)
            }
            .setNegativeButton(t("Abbrechen", "Cancel"), null)
            .setOnDismissListener { resumeScanning() }
            .show()
    }

    private fun showText(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextIsSelectable(true)
            setPadding(60, 40, 60, 20)
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(t("Inhalt des QR-Codes", "QR code content"))
            .setView(tv)
            .setPositiveButton(t("Kopieren", "Copy")) { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QR", text))
                Toast.makeText(this, t("Kopiert", "Copied"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(t("Schließen", "Close"), null)
            .setOnDismissListener { resumeScanning() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        scanner.close()
    }
}
