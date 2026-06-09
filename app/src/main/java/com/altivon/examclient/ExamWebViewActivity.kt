package com.altivon.examclient

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
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

        val cookie = intent.getStringExtra("session_cookie")
        if (cookie != null) {
            val serverIp = AppPreferences.getServerIp(this)
            CookieManager.getInstance().setCookie("http://$serverIp", cookie.split(";")[0])
        }

        val serverIp = AppPreferences.getServerIp(this)
        val port = AppPreferences.getServerPort(this)
        val url = "http://$serverIp:$port/instructions"
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

    override fun onBackPressed() { }  // disable back
}
