package id.arilaprilio.webviewandroid

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application singleton. Holds the shared SettingsStore and applies the saved
 * dark/light mode as early as possible so the WebView does not flash the wrong theme.
 */
class WebViewApp : Application() {

    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

        // Apply saved theme preference immediately — must happen before any activity
        // inflates its view hierarchy.
        AppCompatDelegate.setDefaultNightMode(
            if (settings.dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    companion object {
        private const val PREFS_NAME = "webview_settings"
        lateinit var instance: WebViewApp
            private set
    }
}

/**
 * App settings store backed by SharedPreferences.
 *
 * We intentionally use SharedPreferences (synchronous reads) instead of Jetpack DataStore here
 * because MainActivity needs the current URL during onCreate(); switching to a Flow-based API
 * would force an awkward "render loader → wait → render WebView" split for a value that is
 * written rarely and read on every launch.
 */
class SettingsStore(private val prefs: SharedPreferences) {

    var url: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var autoLoad: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOAD, value).apply()

    var dark: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    /** Cache size budget in MB. 0 disables the cache entirely. */
    var cacheLimitMb: Int
        get() = prefs.getInt(KEY_CACHE_LIMIT, 500)
        set(value) = prefs.edit().putInt(KEY_CACHE_LIMIT, value).apply()

    var autoReload: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RELOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RELOAD, value).apply()

    /** "every" or "hour" — matches the Electron settings vocabulary. */
    var autoReloadMode: String
        get() = prefs.getString(KEY_AUTO_RELOAD_MODE, MODE_EVERY) ?: MODE_EVERY
        set(value) = prefs.edit().putString(KEY_AUTO_RELOAD_MODE, value).apply()

    /** Hour of day (0–23) used when mode == "hour". */
    var autoReloadHour: Int
        get() = prefs.getInt(KEY_AUTO_RELOAD_HOUR, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_RELOAD_HOUR, value.coerceIn(0, 23)).apply()

    /** Duration expression like "1h30m", "45m", "2h". Parsed by DurationParser. */
    var autoReloadEvery: String
        get() = prefs.getString(KEY_AUTO_RELOAD_EVERY, "1h30m") ?: "1h30m"
        set(value) = prefs.edit().putString(KEY_AUTO_RELOAD_EVERY, value).apply()

    var rememberCredentials: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_CREDS, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_CREDS, value).apply()

    companion object {
        const val MODE_EVERY = "every"
        const val MODE_HOUR = "hour"
        const val DEFAULT_URL = "https://www.on-system.net"

        private const val KEY_URL = "settings.url"
        private const val KEY_AUTO_LOAD = "settings.autoLoad"
        private const val KEY_DARK = "settings.dark"
        private const val KEY_CACHE_LIMIT = "settings.cacheLimit"
        private const val KEY_AUTO_RELOAD = "settings.autoReload"
        private const val KEY_AUTO_RELOAD_MODE = "settings.autoReloadMode"
        private const val KEY_AUTO_RELOAD_HOUR = "settings.autoReloadHour"
        private const val KEY_AUTO_RELOAD_EVERY = "settings.autoReloadEvery"
        private const val KEY_REMEMBER_CREDS = "settings.rememberCredentials"
    }
}
