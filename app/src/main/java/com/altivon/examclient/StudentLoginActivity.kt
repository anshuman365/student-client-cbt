package com.altivon.examclient

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.net.HttpURLConnection
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

        val baseUrl = "http://$serverIp:$port/login"
        val query = "system_id=${URLEncoder.encode(systemId, "UTF-8")}&hw=${URLEncoder.encode(hw, "UTF-8")}"
        val fullUrl = "$baseUrl?$query"

        Thread {
            try {
                // Step 1: GET request to initialize session (store system_id, hw)
                val getConn = URL(fullUrl).openConnection() as HttpURLConnection
                getConn.requestMethod = "GET"
                getConn.connect()
                val cookies = getConn.getHeaderField("Set-Cookie")
                getConn.disconnect()

                // Step 2: POST login with credentials (same URL, same query)
                val postConn = URL(fullUrl).openConnection() as HttpURLConnection
                postConn.requestMethod = "POST"
                postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                postConn.doOutput = true
                val params = "roll_number=${URLEncoder.encode(roll, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}"
                postConn.outputStream.bufferedWriter().use { it.write(params) }

                val responseCode = postConn.responseCode
                // Get the final cookie (may be same or updated)
                val finalCookie = postConn.getHeaderField("Set-Cookie") ?: cookies
                postConn.disconnect()

                if (responseCode in 200..302) {
                    runOnUiThread {
                        // Store cookie for WebView
                        if (finalCookie != null) {
                            CookieManager.getInstance().setCookie("http://$serverIp", finalCookie.split(";")[0])
                            CookieManager.getInstance().flush()
                        }
                        // Launch WebView with the same system_id and hw (for banner)
                        val intent = Intent(this, ExamWebViewActivity::class.java).apply {
                            putExtra("system_id", systemId)
                            putExtra("hardware_signature", hw)
                        }
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
