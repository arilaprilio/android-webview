package id.arilaprilio.webviewandroid

import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Reload scheduler mirroring the Electron settings:
 *   mode = "every" → reload every X duration parsed by DurationParser
 *   mode = "hour"  → reload once per day at the configured hour (0–23)
 *
 * Scheduling uses the main-thread Handler to stay in sync with WebView.reload(), which
 * must be invoked on the UI thread. We intentionally avoid WorkManager because:
 *   - The app is always foreground, so we don't need battery-aware deferral.
 *   - WorkManager's minimum periodic interval of 15 minutes is too coarse for "every 1m".
 *   - A single Handler is trivial to cancel + rebuild on settings changes.
 */
class AutoReloadScheduler(
    private val settings: SettingsStore,
    private val onReload: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled: Runnable? = null

    fun reschedule() {
        cancel()
        if (!settings.autoReload) return

        val delay = computeNextDelayMs() ?: return
        val runnable = Runnable {
            onReload()
            // Chain the next tick so "hour" mode re-computes against the clock rather than
            // drifting by exactly 24h from the first invocation.
            reschedule()
        }
        scheduled = runnable
        handler.postDelayed(runnable, delay)
    }

    fun cancel() {
        scheduled?.let { handler.removeCallbacks(it) }
        scheduled = null
    }

    private fun computeNextDelayMs(): Long? = when (settings.autoReloadMode) {
        SettingsStore.MODE_EVERY -> {
            // Fall back to 1h if the user typed garbage; matches the Electron default behaviour
            // where parse-duration returns null and the UI validation prevents saving.
            DurationParser.parseMillis(settings.autoReloadEvery)
                ?: TimeUnit.HOURS.toMillis(1)
        }
        SettingsStore.MODE_HOUR -> millisUntilNextHour(settings.autoReloadHour)
        else -> null
    }

    private fun millisUntilNextHour(hourOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
