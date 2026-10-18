package de.maarthen.qrdirekt

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
            Toast.makeText(this, "Kein https-Link", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Kein Browser gefunden", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
