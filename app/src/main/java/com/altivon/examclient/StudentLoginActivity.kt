package com.altivon.examclient

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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

        val urlString = "http://$serverIp:$port/login?system_id=$systemId&hw=$hw"
        Thread {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                val params = "roll_number=$roll&password=$pass"
                OutputStreamWriter(conn.outputStream).use { it.write(params) }
                val responseCode = conn.responseCode
                val cookieHeader = conn.getHeaderField("Set-Cookie")
                conn.disconnect()

                if (responseCode == 302 || responseCode == 200) {
                    runOnUiThread {
                        val intent = Intent(this, ExamWebViewActivity::class.java).apply {
                            putExtra("session_cookie", cookieHeader)
                        }
                        startActivity(intent)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Login failed. Check roll number/password.", Toast.LENGTH_LONG).show()
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
