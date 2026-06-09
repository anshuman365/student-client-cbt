package com.altivon.examclient

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {

    private const val PREF_FILE = "exam_prefs"
    private const val KEY_SERVER_IP = "server_ip"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_COMPUTER_NUMBER = "computer_number"
    private const val KEY_HARDWARE_ID = "hardware_id"
    private const val KEY_SETUP_DONE = "setup_done"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun isSetupDone(ctx: Context) = prefs(ctx).getBoolean(KEY_SETUP_DONE, false)

    fun saveSetup(ctx: Context, ip: String, port: String, computerNum: String, hwId: String) {
        prefs(ctx).edit()
            .putString(KEY_SERVER_IP, ip)
            .putString(KEY_SERVER_PORT, port)
            .putString(KEY_COMPUTER_NUMBER, computerNum)
            .putString(KEY_HARDWARE_ID, hwId)
            .putBoolean(KEY_SETUP_DONE, true)
            .apply()
    }

    fun getServerIp(ctx: Context) = prefs(ctx).getString(KEY_SERVER_IP, "") ?: ""
    fun getServerPort(ctx: Context) = prefs(ctx).getString(KEY_SERVER_PORT, "5000") ?: "5000"
    fun getComputerNumber(ctx: Context) = prefs(ctx).getString(KEY_COMPUTER_NUMBER, "") ?: ""
    fun getHardwareId(ctx: Context) = prefs(ctx).getString(KEY_HARDWARE_ID, "") ?: ""

    fun clearSetup(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun getExamUrl(ctx: Context): String {
        val ip = getServerIp(ctx)
        val port = getServerPort(ctx)
        val hw = getHardwareId(ctx)
        val compNum = getComputerNumber(ctx)
        val systemId = "ANDROID-$compNum"
        return "http://$ip:$port/login?system_id=$systemId&hw=$hw"
    }

    fun getHeartbeatUrl(ctx: Context): String {
        val ip = getServerIp(ctx)
        val port = getServerPort(ctx)
        return "http://$ip:$port/heartbeat"
    }
}
