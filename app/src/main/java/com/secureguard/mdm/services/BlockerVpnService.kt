package com.secureguard.mdm.services

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

/**
 * VpnService שנועד לחסום את כל תעבורת הרשת.
 * כאשר הוא מופעל, הוא מייצר ממשק רשת וירטואלי אך לא קורא או כותב ממנו,
 * מה שגורם לכל התעבורה "ליפול על הרצפה".
 */
class BlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private val TAG = "BlockerVpnService"

    companion object {
        const val ACTION_CONNECT = "com.secureguard.mdm.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.secureguard.mdm.ACTION_DISCONNECT"
        private const val VPN_PREFS_NAME = "VpnStatePrefs"
        private const val KEY_IS_VPN_ACTIVE = "key_is_vpn_active"

        /**
         * מעדכן את המצב השמור של ה-VPN.
         * השירות עצמו הוא המקור היחיד לאמת לגבי מצבו.
         */
        fun setVpnActive(context: Context, isActive: Boolean) {
            context.getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_VPN_ACTIVE, isActive)
                .apply()
        }

        /**
         * בודק מהו המצב השמור של ה-VPN.
         * משמש את ה-UI כדי לדעת אם המתג צריך להיות דלוק או כבוי.
         */
        fun isVpnActive(context: Context): Boolean {
            return context.getSharedPreferences(VPN_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_IS_VPN_ACTIVE, false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received with action: $action")
        when (action) {
            ACTION_CONNECT -> startVpn()
            ACTION_DISCONNECT -> stopVpn()
        }
        // START_STICKY מבטיח שהמערכת תנסה להפעיל מחדש את השירות אם הוא נהרג.
        return START_STICKY
    }

    private fun startVpn() {
        stopVpn() // עוצר מופע קודם, אם קיים, לפני התחלת אחד חדש.

        vpnThread = Thread {
            try {
                // הגדרת ממשק ה-VPN הווירטואלי.
                val builder = Builder()
                    .addAddress("10.8.0.1", 24) // כתובת IP דמה
                    .addRoute("0.0.0.0", 0)     // מיירט את כל תעבורת ה-IPv4

                // יצירת הממשק. מרגע זה, כל התעבורה מנותבת לכאן.
                vpnInterface = builder.establish()

                if (vpnInterface != null) {
                    setVpnActive(this, true) // מעדכן את המצב כפעיל
                    Log.i(TAG, "VPN interface established. Traffic is now being blocked.")

                    // חלק החסימה: התהליכון נכנס למצב המתנה ופשוט לא עושה כלום,
                    // וכך לא מאפשר לתעבורה לעבור.
                    synchronized(this) {
                        (this as java.lang.Object).wait()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // שחזור סטטוס ה-interrupt
                Log.d(TAG, "VPN thread interrupted.")
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN thread", e)
            } finally {
                Log.d(TAG, "VPN thread is finishing.")
                stopVpn()
            }
        }.apply { name = "BlockerVpnThread" } // נותנים שם לתהליכון לצורך דיבאגינג
        vpnThread?.start()
    }

    private fun stopVpn() {
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        setVpnActive(this, false) // מעדכן את המצב כלא פעיל
        stopSelf() // עוצר את השירות עצמו
    }

    override fun onDestroy() {
        Log.d(TAG, "VpnService is being destroyed.")
        super.onDestroy()
        stopVpn()
    }
}