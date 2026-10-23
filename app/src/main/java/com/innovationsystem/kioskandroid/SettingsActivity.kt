package com.innovationsystem.kioskandroid

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.innovationsystem.kioskandroid.databinding.ActivitySettingsBinding

/**
 * Mirrors the Vue settings page in src/renderer/src/App.vue of the Electron app.
 * The user reaches this screen by swiping from the left edge of MainActivity.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings get() = KioskApp.instance.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        populateFromSettings()
        wireEvents()
    }

    private fun populateFromSettings() = with(binding) {
        fieldUrl.setText(settings.url)
        switchAutoLoad.isChecked = settings.autoLoad
        switchDark.isChecked = settings.dark
        fieldCacheLimit.setText(settings.cacheLimitMb.toString())

        switchAutoReload.isChecked = settings.autoReload
        when (settings.autoReloadMode) {
            SettingsStore.MODE_HOUR -> modeHour.isChecked = true
            else -> modeEvery.isChecked = true
        }
        fieldEvery.setText(settings.autoReloadEvery)
        fieldHour.setText(settings.autoReloadHour.toString())
        switchRememberCreds.isChecked = settings.rememberCredentials

        refreshModeVisibility()
    }

    private fun wireEvents() = with(binding) {
        groupMode.setOnCheckedChangeListener { _, _ -> refreshModeVisibility() }
        switchAutoReload.setOnCheckedChangeListener { _, _ -> refreshModeVisibility() }
        btnCancel.setOnClickListener { finish() }
        btnApply.setOnClickListener { save() }
    }

    private fun refreshModeVisibility() = with(binding) {
        val enabled = switchAutoReload.isChecked
        groupMode.visibility = if (enabled) View.VISIBLE else View.GONE
        val isHour = modeHour.isChecked
        wrapEvery.visibility = if (enabled && !isHour) View.VISIBLE else View.GONE
        wrapHour.visibility = if (enabled && isHour) View.VISIBLE else View.GONE
    }

    private fun save() {
        val urlInput = binding.fieldUrl.text?.toString()?.trim().orEmpty()
        if (urlInput.isBlank()) {
            binding.fieldUrl.error = "Required"
            return
        }

        // "every" mode validation: duration must parse; otherwise keep user on the page.
        if (binding.switchAutoReload.isChecked && binding.modeEvery.isChecked) {
            val every = binding.fieldEvery.text?.toString().orEmpty()
            if (DurationParser.parseMillis(every) == null) {
                binding.fieldEvery.error = "Use format like 1h30m"
                return
            }
        }

        settings.url = urlInput
        settings.autoLoad = binding.switchAutoLoad.isChecked
        settings.dark = binding.switchDark.isChecked
        settings.cacheLimitMb =
            binding.fieldCacheLimit.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 500
        settings.autoReload = binding.switchAutoReload.isChecked
        settings.autoReloadMode =
            if (binding.modeHour.isChecked) SettingsStore.MODE_HOUR else SettingsStore.MODE_EVERY
        settings.autoReloadEvery = binding.fieldEvery.text?.toString().orEmpty()
        settings.autoReloadHour =
            binding.fieldHour.text?.toString()?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        settings.rememberCredentials = binding.switchRememberCreds.isChecked

        // Apply theme change immediately so returning to MainActivity does not flash the old one.
        AppCompatDelegate.setDefaultNightMode(
            if (settings.dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO,
        )
        finish()
    }
}
