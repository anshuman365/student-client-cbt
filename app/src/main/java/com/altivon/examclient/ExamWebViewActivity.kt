package com.altivon.examclient

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ExamWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var autoSubmitTriggered = false
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Skip security checks for debug builds
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            if (SecurityChecker.isRooted() || SecurityChecker.isEmulator() ||
                SecurityChecker.isAdbEnabled(this) || SecurityChecker.isDeveloperOptionsEnabled(this)) {
                showBlockedDialog("Security violation")
                return
            }
        }

        setContentView(R.layout.activity_exam_webview)
        webView = findViewById(R.id.examWebView)
        configureWebView()

        HeartbeatService.examActive = true
        HeartbeatService.autoSubmitCallback = { runOnUiThread { triggerAutoSubmit("Security violation") } }
        val serviceIntent = Intent(this, HeartbeatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val url = AppPreferences.getExamUrl(this)
        Toast.makeText(this, "Loading: $url", Toast.LENGTH_LONG).show()
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            // Use default user agent to avoid server issues
            userAgentString = null
            saveFormData = false
            savePassword = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.isLongClickable = false
        webView.setOnLongClickListener { true }

        // Enable remote debugging (Chrome inspect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("WebView", "${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    Toast.makeText(this@ExamWebViewActivity, "JS: ${it.message()}", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    Toast.makeText(this@ExamWebViewActivity, "Page loaded 100%", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val configuredIp = AppPreferences.getServerIp(this)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val host = request?.url?.host ?: return true
                return host != configuredIp
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Toast.makeText(this@ExamWebViewActivity, "Page finished: $url", Toast.LENGTH_SHORT).show()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val msg = "Error ${error?.errorCode}: ${error?.description}"
                android.util.Log.e("WebView", msg)
                Toast.makeText(this@ExamWebViewActivity, msg, Toast.LENGTH_LONG).show()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val msg = "HTTP ${errorResponse?.statusCode} for ${request?.url}"
                Toast.makeText(this@ExamWebViewActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    override fun onPause() {
        super.onPause()
        if (HeartbeatService.examActive) {
            triggerAutoSubmit("App lost focus")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        triggerAutoSubmit("Home button pressed")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && HeartbeatService.examActive) {
            triggerAutoSubmit("Focus lost")
        }
    }

    override fun onDestroy() {
        HeartbeatService.examActive = false
        HeartbeatService.autoSubmitCallback = null
        stopService(Intent(this, HeartbeatService::class.java))
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated")
    override fun onBackPressed() { }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_MENU -> {
                triggerAutoSubmit("Restricted key")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    fun triggerAutoSubmit(reason: String) {
        if (autoSubmitTriggered) return
        autoSubmitTriggered = true
        HeartbeatService.examActive = false
        Toast.makeText(this, "Auto-submit: $reason", Toast.LENGTH_LONG).show()
        val js = """
            (function() {
                if (typeof saveAnswersSync === 'function') saveAnswersSync();
                setTimeout(function() {
                    var f = document.getElementById('submit-form') || document.querySelector('form');
                    if (f) f.submit();
                }, 500);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        handler.postDelayed({
            stopService(Intent(this, HeartbeatService::class.java))
            finish()
        }, 3000)
    }

    private fun showBlockedDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Cannot Start Exam")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ -> finish() }
            .show()
    }
}
