package com.fmx.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Bookmarks + theme, backed by SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("fmx", Context.MODE_PRIVATE)

    var darkMode: Boolean?
        get() = if (sp.contains("dark")) sp.getBoolean("dark", true) else null
        set(v) = sp.edit { if (v == null) remove("dark") else putBoolean("dark", v) }

    var theme: String
        get() = sp.getString("theme", null) ?: run {
            // migrate legacy toggle
            when (darkMode) {
                false -> "light"
                else -> "system"
            }
        }
        set(v) = sp.edit { putString("theme", v) }

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic", false)
        set(v) = sp.edit { putBoolean("dynamic", v) }

    fun bookmarks(): List<String> =
        sp.getStringSet("bm", emptySet())!!.sorted()

    fun addBookmark(path: String) {
        val s = sp.getStringSet("bm", emptySet())!!.toMutableSet()
        s += path
        sp.edit { putStringSet("bm", s) }
    }

    fun removeBookmark(path: String) {
        val s = sp.getStringSet("bm", emptySet())!!.toMutableSet()
        s -= path
        sp.edit { putStringSet("bm", s) }
    }
}
