package com.antigravity.mobile.system

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * System-bound [DeviceAdminReceiver] backing the `device_control` (`action = "lock"`)
 * MCP tool via [android.app.admin.DevicePolicyManager.lockNow].
 */
class AntigravityDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(LOG_TAG, "Antigravity Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(LOG_TAG, "Antigravity Device Admin disabled")
    }

    companion object {
        private const val LOG_TAG = "AntigravityMobile"

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, AntigravityDeviceAdminReceiver::class.java)
    }
}
