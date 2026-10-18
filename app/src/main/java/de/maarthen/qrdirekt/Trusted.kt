package de.maarthen.qrdirekt

import android.content.Context
import android.net.Uri

/** Vertraute Domains (inkl. Subdomains), die ohne Rückfrage geöffnet werden. */
object Trusted {
    private const val PREFS = "qrdirekt"
    private const val KEY = "domains"
    private const val DEFAULT = "bluenexus-home.de"

    fun list(ctx: Context): List<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Beim ersten Start ist die Standard-Domain vorbelegt (in den Einstellungen entfernbar)
        val set = p.getStringSet(KEY, null) ?: setOf(DEFAULT)
        return set.sorted()
    }

    private fun save(ctx: Context, domains: Collection<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, domains.toSet()).apply()
    }

    /** Macht aus Eingaben wie "https://Nexus.Beispiel.de/abc" den Host "nexus.beispiel.de". */
    fun normalize(input: String): String {
        var s = input.trim().lowercase()
        if (s.contains("://")) s = Uri.parse(s).host.orEmpty() else s = s.substringBefore('/')
        return s.trim('.')
    }

    fun isValid(host: String) =
        Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$").matches(host)

    fun add(ctx: Context, host: String) = save(ctx, list(ctx) + host)

    fun remove(ctx: Context, host: String) = save(ctx, list(ctx) - host)

    /** Exakter Host-Vergleich: Domain selbst oder echte Subdomain, nie "enthält". */
    fun isTrusted(ctx: Context, host: String): Boolean =
        list(ctx).any { host == it || host.endsWith(".$it") }
}
