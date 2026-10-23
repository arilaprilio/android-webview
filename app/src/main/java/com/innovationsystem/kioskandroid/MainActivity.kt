package com.innovationsystem.kioskandroid

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.innovationsystem.kioskandroid.databinding.ActivityMainBinding

/**
 * Fullscreen kiosk host. One WebView, three edge gestures, no browser chrome.
 *
 * Lifecycle contract:
 *   onCreate       → inflate, immersive, build WebView, subscribe gestures
 *   onResume       → re-apply immersive (user may have swiped bars), (re)schedule auto-reload,
 *                    load URL if first run OR autoLoad is true
 *   onPause        → cancel auto-reload
 *   onDestroy      → tear down the WebView to avoid leaking its native pointer
 */
class MainActivity : AppCompatActivity(), EdgeGestureDetector.Callbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var edgeDetector: EdgeGestureDetector
    private lateinit var autoReload: AutoReloadScheduler
    private lateinit var credentials: CredentialStore

    private val settings get() = KioskApp.instance.settings

    private var hasLoadedOnce = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep the screen on while the kiosk is foregrounded — the whole point of kiosk mode.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configureImmersive()
        credentials = CredentialStore(this)
        edgeDetector = EdgeGestureDetector(this, this)
        autoReload = AutoReloadScheduler(settings) { binding.webview.reload() }

        configureWebView(binding.webview)
        wireEdgeToolbar()

        // Decide what to load. If autoLoad is off AND nothing has been configured yet,
        // jump straight to the settings screen (mirrors Electron behaviour where the
        // settings window shows up on first launch).
        if (!settings.autoLoad && savedInstanceState == null) {
            openSettings()
        } else {
            loadConfiguredUrl()
        }
    }

    override fun onResume() {
        super.onResume()
        configureImmersive()
        autoReload.reschedule()
        // If the user changed the URL in SettingsActivity and came back, reload it.
        val current = binding.webview.url
        if (current == null || current != settings.url) loadConfiguredUrl()
    }

    override fun onPause() {
        super.onPause()
        autoReload.cancel()
    }

    override fun onDestroy() {
        // Detach from view hierarchy before destroy() to avoid "WebView.destroy called while
        // still attached" warnings on some OEM builds.
        (binding.webview.parent as? android.view.ViewGroup)?.removeView(binding.webview)
        binding.webview.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // In kiosk mode the system Back button navigates the WebView history only; it must
        // never close the activity.
        if (binding.webview.canGoBack()) binding.webview.goBack()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (edgeDetector.onTouch(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    // ---- Gesture callbacks ------------------------------------------------------------------

    override fun onLeftEdgeSwipe() = openSettings()

    override fun onRightEdgeSwipe() = toggleSoftKeyboard()

    override fun onBottomEdgeSwipe() {
        binding.edgeToolbar.visibility =
            if (binding.edgeToolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    override fun onCornerLongPress() {
        // Per user decision: long-press top-left exits kiosk without a PIN prompt.
        finishAffinity()
    }

    // ---- WebView wiring ---------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            // Cache size budget — electron-kiosk uses MB; WebView API uses bytes.
            cacheMode = WebSettings.LOAD_DEFAULT
            // Mixed content allowed because kiosks commonly load intranet HTTP alongside HTTPS.
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = false
            // User-Agent kept as system default; override here if the backend requires it.
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                binding.loader.visibility = View.VISIBLE
                binding.loaderText.text = getString(R.string.loading_url, url ?: "")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                hasLoadedOnce = true
                binding.loader.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                // Only react to failures of the main frame; sub-resource errors are noisy.
                if (!request.isForMainFrame) return
                binding.loader.visibility = View.VISIBLE
                binding.loaderText.text = getString(R.string.url_unreachable)
                // Retry in 5s. Matches the loader-with-retry behaviour of the Electron app.
                view.postDelayed({ loadConfiguredUrl() }, 5_000)
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String,
            ) {
                val key = CredentialStore.keyFor(host, realm)
                if (settings.rememberCredentials) {
                    credentials.get(key)?.let { saved ->
                        handler.proceed(saved.username, saved.password)
                        return
                    }
                }
                AuthDialog.show(
                    context = this@MainActivity,
                    host = host,
                    realm = realm,
                    handler = handler,
                    rememberDefault = settings.rememberCredentials,
                ) { u, p -> credentials.put(key, CredentialStore.Credential(u, p)) }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                // Keep all navigation inside the WebView — never spawn external browser.
                // Intercept tel:, mailto:, intent: so the kiosk cannot leak to other apps.
                val scheme = request.url.scheme ?: return false
                return scheme !in setOf("http", "https", "about", "data", "blob")
            }
        }
    }

    private fun wireEdgeToolbar() {
        binding.btnReload.setOnClickListener { binding.webview.reload() }
        binding.btnBack.setOnClickListener {
            if (binding.webview.canGoBack()) binding.webview.goBack()
        }
        binding.btnHome.setOnClickListener { loadConfiguredUrl() }
        binding.btnKeyboard.setOnClickListener { toggleSoftKeyboard() }
        binding.btnSettings.setOnClickListener { openSettings() }
    }

    private fun loadConfiguredUrl() {
        val url = settings.url.ifBlank { SettingsStore.DEFAULT_URL }
        binding.loader.visibility = View.VISIBLE
        binding.loaderText.text = getString(R.string.loading_url, url)
        binding.webview.loadUrl(url)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun toggleSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        // Give focus to the WebView first so the IME has a target.
        binding.webview.requestFocus()
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun configureImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
