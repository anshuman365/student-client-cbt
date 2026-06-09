package com.altivon.examclient

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class StudentLoginActivity : AppCompatActivity() {

    private lateinit var etRoll: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_login)

        // Enable a global CookieManager for all HTTP requests (including WebView)
        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)

        etRoll = findViewById(R.id.etRollNumber)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)

        btnLogin.setOnClickListener {
            val roll = etRoll.text.toString().trim()
            val pass = etPassword.text.toString()
            if (roll.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter roll number and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performLogin(roll, pass)
        }
    }

    private fun performLogin(roll: String, pass: String) {
        btnLogin.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE

        val serverIp = AppPreferences.getServerIp(this)
        val port = AppPreferences.getServerPort(this)
        val hw = AppPreferences.getHardwareId(this)
        val compNum = AppPreferences.getComputerNumber(this)
        val systemId = "ANDROID-$compNum"

        // URL with query parameters (system_id and hw) as required by server
        val urlString = "http://$serverIp:$port/login?system_id=${URLEncoder.encode(systemId, "UTF-8")}&hw=${URLEncoder.encode(hw, "UTF-8")}"

        Thread {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                val params = "roll_number=${URLEncoder.encode(roll, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}"
                conn.outputStream.bufferedWriter().use { it.write(params) }

                val responseCode = conn.responseCode
                // The CookieManager will automatically store cookies from the response
                conn.disconnect()

                if (responseCode in 200..302) {
                    runOnUiThread {
                        // After successful login, launch WebView
                        val intent = Intent(this, ExamWebViewActivity::class.java)
                        // Pass system_id and hw for the WebView URL
                        intent.putExtra("system_id", systemId)
                        intent.putExtra("hardware_signature", hw)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Login failed (HTTP $responseCode). Check credentials.", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        progressBar.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnLogin.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                }
            }
        }.start()
    }
}
