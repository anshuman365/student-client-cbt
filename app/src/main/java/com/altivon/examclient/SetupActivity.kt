package com.altivon.examclient

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etIp = findViewById<EditText>(R.id.etServerIp)
        val etPort = findViewById<EditText>(R.id.etServerPort)
        val etComputer = findViewById<EditText>(R.id.etComputerNumber)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val btnSave = findViewById<Button>(R.id.btnSaveSetup)
        val btnReset = findViewById<Button>(R.id.btnReset)

        val hwId = DeviceFingerprint.generate(this)
        tvDeviceId.text = "Device ID: ${hwId.take(16)}…"

        etPort.setText("5000")

        if (SecurityChecker.isRooted()) {
            Toast.makeText(this, "⚠ Rooted device detected. Cannot run exam.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (SecurityChecker.isEmulator()) {
            Toast.makeText(this, "⚠ Emulator detected. Cannot run exam.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnSave.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim().ifBlank { "5000" }
            val compNum = etComputer.text.toString().trim()

            if (TextUtils.isEmpty(ip)) {
                etIp.error = "Server IP is required"
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(compNum)) {
                etComputer.error = "Computer / Seat number is required"
                return@setOnClickListener
            }

            AppPreferences.saveSetup(this, ip, port, compNum, hwId)
            Toast.makeText(this, "Setup saved. Starting exam client…", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ExamWebViewActivity::class.java))
            finish()
        }

        btnReset.setOnClickListener {
            AppPreferences.clearSetup(this)
            Toast.makeText(this, "Configuration cleared.", Toast.LENGTH_SHORT).show()
        }
    }
}
