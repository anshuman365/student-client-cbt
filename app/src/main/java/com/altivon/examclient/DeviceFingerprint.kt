package com.altivon.examclient

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceFingerprint {

    @SuppressLint("HardwareIds")
    fun generate(ctx: Context): String {
        val androidId = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_id"

        val board = Build.BOARD ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val product = Build.PRODUCT ?: ""
        val fingerprint = Build.FINGERPRINT ?: ""

        @Suppress("DEPRECATION")
        val serial = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                Build.SERIAL
            }
        } catch (e: Exception) {
            "no_serial"
        }

        val raw = "$androidId|$board|$brand|$device|$model|$manufacturer|$product|$fingerprint|$serial"
        return sha256(raw)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
