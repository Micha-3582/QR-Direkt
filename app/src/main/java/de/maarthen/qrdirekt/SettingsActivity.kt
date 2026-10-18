package de.maarthen.qrdirekt

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    private fun t(de: String, en: String) = Lang.t(this, de, en)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f, top: Int = 0) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, dp(top), 0, dp(4))
    }

    private fun render() {
        root.removeAllViews()
        title = t("QR Direkt – Einstellungen", "QR Direkt – Settings")
        val flags = LinearLayout(this).apply { gravity = Gravity.END }
        for ((code, flag) in listOf("de" to "🇩🇪", "en" to "🇬🇧")) {
            flags.addView(TextView(this).apply {
                text = flag
                textSize = 28f
                setPadding(dp(10), dp(4), dp(10), dp(4))
                alpha = if (Lang.current(this@SettingsActivity) == code) 1f else 0.35f
                contentDescription = if (code == "de") "Deutsch" else "English"
                setOnClickListener { Lang.set(this@SettingsActivity, code); render() }
            })
        }
        root.addView(flags)
        root.addView(label(t("Vertraute Domains", "Trusted domains"), 22f))
        root.addView(label(t("Links dieser Domains (und ihrer Subdomains) öffnen sofort ohne Rückfrage. Alle anderen https-Links fragen vorher nach.", "Links from these domains (and their subdomains) open immediately without asking. All other https links ask first.")))

        val domains = Trusted.list(this)
        if (domains.isEmpty()) root.addView(label(t("Noch keine Domain eingetragen.", "No domain added yet."), top = 12))
        for (d in domains) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply { text = d; textSize = 18f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = t("Entfernen", "Remove")
                setOnClickListener { Trusted.remove(this@SettingsActivity, d); render() }
            })
            root.addView(row)
        }

        root.addView(label(t("Domain hinzufügen", "Add domain"), 18f, 24))
        val input = EditText(this).apply {
            hint = t("z. B. meine-domain.de", "e.g. my-domain.com")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        root.addView(input)
        root.addView(Button(this).apply {
            text = t("Hinzufügen", "Add")
            setOnClickListener {
                val host = Trusted.normalize(input.text.toString())
                if (Trusted.isValid(host)) { Trusted.add(this@SettingsActivity, host); render() }
                else Toast.makeText(this@SettingsActivity, t("Keine gültige Domain", "Not a valid domain"), Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = t("Per QR-Code hinzufügen", "Add by QR code")
            setOnClickListener { scanDomain() }
        })

        root.addView(label(t("Hinweis: Links öffnen im Standardbrowser deines Handys. Manche Seiten (z. B. mit Anmeldung oder Registrierung) funktionieren nur im selben Browser, in dem du dich zuvor angemeldet hast.", "Note: Links open in your phone's default browser. Some sites (e.g. with login or registration) only work in the same browser you signed in with before."), 14f, 24))

        root.addView(TextView(this).apply {
            text = "GitHub: Micha-3582"
            textSize = 16f
            setTextColor(0xFF6EA8FE.toInt())
            setPadding(0, dp(24), 0, dp(8))
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Micha-3582")))
            }
        })
    }

    private fun scanDomain() {
        startActivityForResult(
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PICK, true), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val raw = data?.getStringExtra(MainActivity.EXTRA_RESULT).orEmpty().trim()
        val host = if (raw.startsWith("https://", true)) Trusted.normalize(raw) else ""
        if (Trusted.isValid(host)) {
            Trusted.add(this, host)
            Toast.makeText(this, t("Hinzugefügt: $host", "Added: $host"), Toast.LENGTH_SHORT).show()
            render()
        } else {
            Toast.makeText(this, t("Kein https-Link", "Not an https link"), Toast.LENGTH_LONG).show()
        }
    }
}
