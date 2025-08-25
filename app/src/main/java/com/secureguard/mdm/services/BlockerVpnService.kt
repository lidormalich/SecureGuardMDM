package com.secureguard.mdm.services

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.secureguard.mdm.R
import java.io.IOException

class BlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private val TAG = "BlockerVpnService"

    companion object {
        const val ACTION_CONNECT = "com.secureguard.mdm.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.secureguard.mdm.ACTION_DISCONNECT"
        private const val VPN_PREFS_NAME = "VpnStatePrefs"
        private const val KEY_IS_VPN_ACTIVE = "key_is_vpn_active"

        fun setVpnActive(context: Context, isActive: Boolean) {
            context.getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_VPN_ACTIVE, isActive)
                .apply()
        }

        fun isVpnActive(context: Context): Boolean {
            return context.getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_IS_VPN_ACTIVE, false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received with action: $action")

        if (action == ACTION_DISCONNECT) {
            stopVpn()
        } else {
            startVpn()
        }
        return START_STICKY
    }

    // --- פונקציה חדשה לניקוי משאבים בלבד ---
    private fun cleanup() {
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        setVpnActive(this, false)
        Log.d(TAG, "VPN resources cleaned up.")
    }

    private fun startVpn() {
        if (vpnThread?.isAlive == true) {
            Log.d(TAG, "VPN is already running, no need to start again.")
            return
        }

        // --- התיקון המרכזי: קוראים ל-cleanup במקום ל-stopVpn ---
        // זה מנקה חיבורים ישנים מבלי להרוג את השירות הנוכחי.
        cleanup()

        vpnThread = Thread {
            try {
                val builder = Builder()
                    .addAddress("10.0.0.2", 24)
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .setMtu(1500)
                    .setSession(getString(R.string.app_name))

                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    setVpnActive(this, true)
                    Log.i(TAG, "VPN interface established. Traffic is now being blocked.")
                    synchronized(this) {
                        (this as java.lang.Object).wait()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.d(TAG, "VPN thread interrupted.")
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN thread", e)
            } finally {
                Log.d(TAG, "VPN thread is finishing.")
            }
        }.apply { name = "BlockerVpnThread" }
        vpnThread?.start()
    }

    // --- stopVpn אחראית מעכשיו על ניקוי ועצירה מלאה של השירות ---
    private fun stopVpn() {
        cleanup()
        stopSelf()
        Log.i(TAG, "VPN stopped and service is self-stopping.")
    }

    override fun onDestroy() {
        Log.d(TAG, "VpnService is being destroyed.")
        super.onDestroy()
        // כשאין ברירה והמערכת הורגת את השירות, לפחות ננקה אחרינו.
        cleanup()
    }
}