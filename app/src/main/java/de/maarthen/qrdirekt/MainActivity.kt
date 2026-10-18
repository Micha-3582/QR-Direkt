package de.maarthen.qrdirekt

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode -> openUrl(barcode.rawValue) }
            .addOnCanceledListener { finish() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scanner nicht verfügbar: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun openUrl(value: String?) {
        val url = value?.trim().orEmpty()
        if (!url.startsWith("https://", ignoreCase = true)) {
            showText(value.orEmpty())
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Kein Browser gefunden", Toast.LENGTH_LONG).show()
        }
        finish()
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
}
