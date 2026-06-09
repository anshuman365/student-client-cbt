package com.altivon.examclient

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ExamWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_webview)

        webView = findViewById(R.id.examWebView)
        configureWebView()

        // Ensure WebView uses the same cookie store as the global CookieManager
        CookieManager.getInstance().setAcceptCookie(true)
        // Sync cookies from global CookieManager (they are already stored by HttpURLConnection)
        // No need to manually set – the WebView will use the same cookie jar.

        val systemId = intent.getStringExtra("system_id") ?: AppPreferences.getComputerNumber(this).let { "ANDROID-$it" }
        val hw = intent.getStringExtra("hardware_signature") ?: AppPreferences.getHardwareId(this)
        val serverIp = AppPreferences.getServerIp(this)
        val port = AppPreferences.getServerPort(this)

        // Load instructions with system_id and hw for the banner (optional but good)
        val url = "http://$serverIp:$port/instructions?system_id=$systemId&hw=$hw"
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = WebViewClient()
    }

    override fun onBackPressed() { }  // disable back button
}
