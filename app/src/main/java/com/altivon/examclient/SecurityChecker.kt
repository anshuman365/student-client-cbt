package com.altivon.examclient

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File

object SecurityChecker {

    fun isRooted(): Boolean {
        return checkTestKeys()
            || checkSuperuserFiles()
            || checkSuBinary()
            || checkMagisk()
    }

    private fun checkTestKeys(): Boolean =
        Build.TAGS?.contains("test-keys") == true

    private fun checkSuperuserFiles(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/data/app/com.topjohnwu.magisk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkSuBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val result = process.inputStream.bufferedReader().readLine()
            process.destroy()
            !result.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkMagisk(): Boolean {
        val magiskPaths = listOf(
            "/sbin/.magisk",
            "/sbin/.core/mirror",
            "/sbin/.core/img",
            "/cache/.disable_magisk"
        )
        return magiskPaths.any { File(it).exists() }
    }

    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""

        return (fp.startsWith("generic") || fp.startsWith("unknown"))
            || model.contains("google_sdk")
            || model.contains("Emulator")
            || model.contains("Android SDK built for x86")
            || manufacturer.contains("Genymotion")
            || (brand.startsWith("generic") && device.startsWith("generic"))
            || product.equals("google_sdk")
    }

    fun isAdbEnabled(ctx: Context): Boolean {
        return Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) == 1
    }

    fun isDeveloperOptionsEnabled(ctx: Context): Boolean {
        return Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
    }
}
