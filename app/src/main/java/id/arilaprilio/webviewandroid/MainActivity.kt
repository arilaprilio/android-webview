package id.arilaprilio.webviewandroid

import android.annotation.SuppressLint
import android.content.Intent
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
import id.arilaprilio.webviewandroid.databinding.ActivityMainBinding

/**
 * Fullscreen WebView host. A small semi-transparent FAB gives access to the
 * navigation toolbar (reload, back, home, keyboard, settings).
 *
 * Lifecycle contract:
 *   onCreate       → inflate, immersive, build WebView, wire FAB + toolbar
 *   onResume       → re-apply immersive, (re)schedule auto-reload,
 *                    load URL if first run OR autoLoad is true
 *   onPause        → cancel auto-reload
 *   onDestroy      → tear down the WebView to avoid leaking its native pointer
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var autoReload: AutoReloadScheduler
    private lateinit var credentials: CredentialStore

    private val settings get() = WebViewApp.instance.settings

    private var hasLoadedOnce = false
    private var pendingHistoryClear = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep the screen on while the app is foregrounded.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        configureImmersive()
        credentials = CredentialStore(this)
        autoReload = AutoReloadScheduler(settings) { binding.webview.reload() }

        configureWebView(binding.webview)
        wireFabAndToolbar()

        // Decide what to load. If autoLoad is off AND nothing has been configured yet,
        // jump straight to the settings screen so the user can set a URL.
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
        // If the user changed the URL in SettingsActivity and came back, reload it
        // and clear stale history so Back doesn't resurface the old URL.
        val current = binding.webview.url
        if (current == null || current != settings.url) {
            pendingHistoryClear = true
            loadConfiguredUrl()
        }
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // The system Back button navigates the WebView history only; it must
        // never close the activity.
        if (binding.webview.canGoBack()) binding.webview.goBack()
    }

    // ---- FAB & Toolbar ----------------------------------------------------------------------

    private fun wireFabAndToolbar() {
        // Tap FAB → toggle toolbar visibility
        binding.fabMenu.setOnClickListener {
            toggleToolbar()
        }

        // Tap anywhere on the WebView while toolbar is visible → hide toolbar
        binding.webview.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN &&
                binding.edgeToolbar.visibility == View.VISIBLE
            ) {
                hideToolbar()
                // Don't consume — let the WebView also handle the touch
            }
            false
        }

        // Toolbar button actions
        binding.btnReload.setOnClickListener {
            binding.webview.reload()
            hideToolbar()
        }
        binding.btnBack.setOnClickListener {
            if (binding.webview.canGoBack()) binding.webview.goBack()
            hideToolbar()
        }
        binding.btnHome.setOnClickListener {
            loadConfiguredUrl()
            hideToolbar()
        }
        binding.btnKeyboard.setOnClickListener {
            toggleSoftKeyboard()
            hideToolbar()
        }
        binding.btnSettings.setOnClickListener {
            openSettings()
            hideToolbar()
        }
    }

    private fun toggleToolbar() {
        if (binding.edgeToolbar.visibility == View.VISIBLE) {
            hideToolbar()
        } else {
            showToolbar()
        }
    }

    private fun showToolbar() {
        binding.edgeToolbar.visibility = View.VISIBLE
        binding.fabMenu.hide() // hide FAB while toolbar is showing
    }

    private fun hideToolbar() {
        binding.edgeToolbar.visibility = View.GONE
        binding.fabMenu.show() // show FAB again
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
            cacheMode = WebSettings.LOAD_DEFAULT
            // Mixed content allowed because apps commonly load intranet HTTP alongside HTTPS.
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true)
            builtInZoomControls = false
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                binding.loader.visibility = View.VISIBLE
                binding.loaderText.text = getString(R.string.loading_url, url ?: "")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                hasLoadedOnce = true
                binding.loader.visibility = View.GONE
                // Clear history after loading a new configured URL so the Back button
                // doesn't navigate to the previous/default URL.
                if (pendingHistoryClear) {
                    pendingHistoryClear = false
                    view.clearHistory()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                binding.loader.visibility = View.VISIBLE
                binding.loaderText.text = getString(R.string.url_unreachable)
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
                val scheme = request.url.scheme ?: return false
                return scheme !in setOf("http", "https", "about", "data", "blob")
            }
        }
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
