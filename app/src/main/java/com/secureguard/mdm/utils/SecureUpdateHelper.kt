package com.secureguard.mdm.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

// ###################################################################################
// #################################  WARNING!  #####################################
// ###################################################################################
// # DO NOT MODIFY THIS FILE UNLESS YOU KNOW EXACTLY WHAT YOU ARE DOING.
// # This file contains critical app integrity logic.
// # Any changes can lead to app instability, security vulnerabilities, or
// # cause the app to fail to start.
// #
// # אזהרה! אין לשנות קובץ זה אלא אם כן אתה יודע בדיוק מה אתה עושה.
// # קובץ זה מכיל לוגיקה קריטית לשלמות האפליקציה.
// # כל שינוי עלול להוביל לחוסר יציבות, פרצות אבטחה, או לגרום לאפליקציה
// # להיכשל בעלייה.
// ###################################################################################


sealed class UpdateVerificationResult {
    data class Success(val packageName: String) : UpdateVerificationResult()
    data class Failure(val errorMessage: String) : UpdateVerificationResult()
}

@Singleton
class SecureUpdateHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pm: PackageManager = context.packageManager


    companion object {
        private val VALIDATION_ARRAY: IntArray = intArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        )

        // This key is used for the simple XOR obfuscation/de-obfuscation.
        private const val TRANSFORM_FACTOR = 123
    }

    /**
     * Checks if the currently running application has the official signature.
     * This is done by de-obfuscating the stored validation array and comparing its
     * hash to the hash of the current application's signature.
     */
    fun isLegitimate(): Boolean {
        // Prevent running on emulators or in debug builds for this check if needed
        // if (BuildConfig.DEBUG) return true // Or false, depending on desired behavior

        val currentSignatureHash = getAppSignatureSha256(context) ?: return false
        val officialSignatureHash = deobfuscate()

        return currentSignatureHash == officialSignatureHash
    }

    /**
     * De-obfuscates the VALIDATION_ARRAY back to the original SHA-256 hash string.
     */
    private fun deobfuscate(): String {
        return VALIDATION_ARRAY
            .map { it xor TRANSFORM_FACTOR }
            .map { it.toByte() }
            .toByteArray()
            .joinToString("") { "%02x".format(it) }
    }

    private fun getAppSignatureSha256(context: Context): String? {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.firstOrNull()?.toByteArray()?.let {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(it)
                return hash.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e("SignatureCheck", "Failed to get app signature hash", e)
        }
        return null
    }

    /**
     * A simple flag function to verify the integrity and existence of this critical component at startup.
     */
    fun coreComponentExists(): Boolean = true

    // --- סוף: לוגיקת אימות חתימה רשמית (מטושטשת) ---


    fun verifyUpdate(apkUri: Uri): UpdateVerificationResult {
        var localApkPath: String? = null
        try {
            context.contentResolver.openInputStream(apkUri)?.use { inputStream ->
                context.openFileOutput("temp.apk", Context.MODE_PRIVATE).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            localApkPath = context.getFileStreamPath("temp.apk").absolutePath

            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(localApkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(localApkPath, PackageManager.GET_SIGNATURES)
            } ?: return UpdateVerificationResult.Failure("לא ניתן היה לקרוא את קובץ ה-APK.")

            val apkSignatures = getSignaturesFromPackageInfo(archiveInfo)
            if (apkSignatures.isEmpty()) return UpdateVerificationResult.Failure("לא נמצאה חתימה בקובץ ה-APK.")
            val apkSignature = apkSignatures.first()
            val packageName = archiveInfo.packageName

            val installedPackageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return UpdateVerificationResult.Failure("האפליקציה המיועדת לעדכון אינה מותקנת.")
            }

            val installedSignatures = getSignaturesFromPackageInfo(installedPackageInfo)
            if (installedSignatures.isEmpty()) return UpdateVerificationResult.Failure("לא נמצאה חתימה באפליקציה המותקנת.")
            val installedSignature = installedSignatures.first()

            return if (apkSignature == installedSignature) {
                UpdateVerificationResult.Success(packageName)
            } else {
                UpdateVerificationResult.Failure("שגיאה: חתימת העדכון אינה תואמת לאפליקציה המותקנת.")
            }

        } catch (e: Exception) {
            Log.e("SecureUpdateHelper", "Verification failed", e)
            return UpdateVerificationResult.Failure("שגיאה בקריאת קובץ ה-APK.")
        } finally {
            localApkPath?.let { context.deleteFile("temp.apk") }
        }
    }

    private fun getSignaturesFromPackageInfo(packageInfo: PackageInfo): Array<out Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: emptyArray()
        }
    }
}