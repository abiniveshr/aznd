package com.ashrdev.aznd.ui.calendar

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Favourite days live in SharedPreferences, like the theme, so this feature needs no database
// change (and can't wipe your data through a schema bump). Each entry is "scope|yyyy-MM-dd",
// where the scope names what the calendar belongs to, e.g. "workout:3" or "streak:1".
object FavoriteDayStore {
    private const val PREFS = "aznd_favorite_days"
    private const val KEY = "days"

    private val _entries = MutableStateFlow<Set<String>>(emptySet())
    val entries: StateFlow<Set<String>> = _entries.asStateFlow()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _entries.value = p.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    }

    fun toggle(scope: String, day: String) {
        val entry = "$scope|$day"
        val next = _entries.value.toMutableSet()
        if (!next.add(entry)) next.remove(entry)
        _entries.value = next
        prefs?.edit()?.putStringSet(KEY, HashSet(next))?.apply()
    }
}

class FavoriteDays(val days: Set<String>, private val scope: String) {
    fun isFavorite(day: String): Boolean = day in days
    fun toggle(day: String) = FavoriteDayStore.toggle(scope, day)
}

/** The favourite days for one calendar scope, live. */
@Composable
fun rememberFavoriteDays(scope: String): FavoriteDays {
    val context = LocalContext.current
    remember(context) { FavoriteDayStore.init(context) }
    val all by FavoriteDayStore.entries.collectAsState()
    val days = remember(all, scope) {
        val prefix = "$scope|"
        all.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }
    return remember(days, scope) { FavoriteDays(days, scope) }
}
