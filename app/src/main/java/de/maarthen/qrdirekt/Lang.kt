package de.maarthen.qrdirekt

import android.content.Context
import java.util.Locale

/** Einfache Zweisprachigkeit (Deutsch/Englisch), umschaltbar in den Einstellungen. */
object Lang {
    private const val PREFS = "qrdirekt"
    private const val KEY = "lang"

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: if (Locale.getDefault().language == "de") "de" else "en"

    fun set(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    fun t(ctx: Context, de: String, en: String) = if (current(ctx) == "de") de else en
}
