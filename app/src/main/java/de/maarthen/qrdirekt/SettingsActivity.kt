package de.maarthen.qrdirekt

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QR Direkt – Einstellungen"
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f, top: Int = 0) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, dp(top), 0, dp(4))
    }

    private fun render() {
        root.removeAllViews()
        root.addView(label("Vertraute Domains", 22f))
        root.addView(label("Links dieser Domains (und ihrer Subdomains) öffnen sofort ohne Rückfrage. Alle anderen https-Links fragen vorher nach."))

        val domains = Trusted.list(this)
        if (domains.isEmpty()) root.addView(label("Noch keine Domain eingetragen.", top = 12))
        for (d in domains) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply { text = d; textSize = 18f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Entfernen"
                setOnClickListener { Trusted.remove(this@SettingsActivity, d); render() }
            })
            root.addView(row)
        }

        root.addView(label("Domain hinzufügen", 18f, 24))
        val input = EditText(this).apply {
            hint = "z. B. nexus.meine-domain.de"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "Hinzufügen"
            setOnClickListener {
                val host = Trusted.normalize(input.text.toString())
                if (Trusted.isValid(host)) { Trusted.add(this@SettingsActivity, host); render() }
                else Toast.makeText(this@SettingsActivity, "Keine gültige Domain", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Per QR-Code hinzufügen"
            setOnClickListener { scanDomain() }
        })

        root.addView(label("Hinweis: Der Link öffnet im Standardbrowser deines Handys. Bei BlueNexus muss das derselbe Browser sein, in dem das Handy registriert wurde.", 14f, 24))
    }

    private fun scanDomain() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { b ->
                val raw = b.rawValue.orEmpty().trim()
                val host = if (raw.startsWith("https://", true)) Trusted.normalize(raw) else ""
                if (Trusted.isValid(host)) {
                    Trusted.add(this, host)
                    Toast.makeText(this, "Hinzugefügt: $host", Toast.LENGTH_SHORT).show()
                    render()
                } else {
                    Toast.makeText(this, "Kein https-Link", Toast.LENGTH_LONG).show()
                }
            }
    }
}
