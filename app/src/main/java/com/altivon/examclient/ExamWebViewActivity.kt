package com.altivon.examclient

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (SecurityChecker.isRooted()) {
            showBlockedDialog("Rooted device detected. Exam cannot start.")
            return
        }
        if (SecurityChecker.isEmulator()) {
            showBlockedDialog("Emulator detected. Exam cannot start.")
            return
        }
        if (SecurityChecker.isAdbEnabled(this)) {
            showBlockedDialog("USB Debugging is enabled. Disable it and restart.")
            return
        }
        if (SecurityChecker.isDeveloperOptionsEnabled(this)) {
            showBlockedDialog("Developer Options are enabled. Disable them and restart.")
            return
        }

        setContentView(R.layout.activity_exam_webview)
        webView = findViewById(R.id.examWebView)
        configureWebView()

        HeartbeatService.examActive = true
        HeartbeatService.autoSubmitCallback = { runOnUiThread { triggerAutoSubmit("Security violation detected") } }
        val serviceIntent = Intent(this, HeartbeatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val url = AppPreferences.getExamUrl(this)
        webView.loadUrl(url)
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
            triggerAutoSubmit("App lost focus / moved to background")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        triggerAutoSubmit("Home button pressed")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && HeartbeatService.examActive) {
            triggerAutoSubmit("Window focus lost")
        }
    }

    override fun onDestroy() {
        HeartbeatService.examActive = false
        HeartbeatService.autoSubmitCallback = null
        stopService(Intent(this, HeartbeatService::class.java))
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Disable back button
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_MENU -> {
                triggerAutoSubmit("Restricted key pressed: $keyCode")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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
            userAgentString = "ExamClient/1.0 Android"
            saveFormData = false
            savePassword = false
        }

        webView.isLongClickable = false
        webView.setOnLongClickListener { true }

        val configuredIp = AppPreferences.getServerIp(this)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val host = request?.url?.host ?: return true
                return host != configuredIp
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Toast.makeText(
                    this@ExamWebViewActivity,
                    "Connection error: $description",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun triggerAutoSubmit(reason: String) {
        if (autoSubmitTriggered) return
        autoSubmitTriggered = true
        HeartbeatService.examActive = false

        Toast.makeText(this, "⚠ Exam auto-submitted: $reason", Toast.LENGTH_LONG).show()

        val js = """
            (function() {
                if (typeof saveAnswersSync === 'function') {
                    saveAnswersSync();
                }
                setTimeout(function() {
                    var form = document.getElementById('submit-form');
                    if (form) {
                        form.submit();
                    } else {
                        var forms = document.getElementsByTagName('form');
                        if (forms.length > 0) forms[0].submit();
                    }
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
