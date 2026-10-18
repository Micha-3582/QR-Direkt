package de.maarthen.qrdirekt

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
    private lateinit var root: FrameLayout

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
            text = "QR Direkt braucht die Kamera, um QR-Codes zu lesen."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })
        box.addView(Button(this).apply {
            text = "App-Einstellungen öffnen"
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
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val frame = View(this).apply {
            background = GradientDrawable().apply {
                setStroke(dp(3), Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
        }
        root.addView(frame, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))

        root.addView(TextView(this).apply {
            text = "QR-Code in den Rahmen halten"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(0x88000000.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(48)
        })
        addGear()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                val media = proxy.image
                if (media == null || handled) { proxy.close(); return@setAnalyzer }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val value = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }?.rawValue
                        if (value != null && !handled) {
                            handled = true
                            runOnUiThread { onScanned(value) }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, prev, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera nicht verfügbar: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
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
            contentDescription = "Einstellungen"
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
            Toast.makeText(this, "Kein Browser gefunden", Toast.LENGTH_LONG).show()
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
            .setTitle("Diese Seite öffnen?")
            .setView(tv)
            .setPositiveButton("Öffnen") { _, _ -> launch(url) }
            .setNeutralButton("Immer öffnen") { _, _ ->
                Trusted.add(this, host)
                launch(url)
            }
            .setNegativeButton("Abbrechen", null)
            .setOnDismissListener { if (!isFinishing) finish() }
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
            .setTitle("Inhalt des QR-Codes")
            .setView(tv)
            .setPositiveButton("Kopieren") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QR", text))
                Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Schließen", null)
            .setOnDismissListener { finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        scanner.close()
    }
}
