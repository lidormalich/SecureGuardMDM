package com.secureguard.mdm.utils.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.secureguard.mdm.R
import com.secureguard.mdm.utils.SecureUpdateHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UpdateManager"
private const val UPDATE_FILE_NAME = "update.apk"

sealed class UpdateResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Failure(val message: String) : UpdateResult()
}

sealed class DownloadResult {
    object Success : DownloadResult()
    data class Failure(val message: String) : DownloadResult()
}


@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureUpdateHelper: SecureUpdateHelper
) {

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        if (!secureUpdateHelper.isOfficialBuild()) {
            Log.d(TAG, "Update check skipped: Not an official build.")
            return@withContext UpdateResult.NoUpdate
        }

        try {
            val remoteVersionCode = URL(context.getString(R.string.update_version_url)).readText().trim().toIntOrNull()
                ?: throw Exception("Invalid version format from server")

            val currentVersionCode = context.getString(R.string.app_version_code).toIntOrNull() ?: 1

            Log.d(TAG, "Remote version: $remoteVersionCode, Current version: $currentVersionCode")

            if (remoteVersionCode > currentVersionCode) {
                Log.i(TAG, "Update available. Fetching changelog...")
                val changelog = URL(context.getString(R.string.update_changelog_url)).readText().trim()
                val downloadUrl = context.getString(R.string.update_apk_download_url)

                val updateInfo = UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = "Unknown",
                    changelog = changelog,
                    downloadUrl = downloadUrl
                )
                return@withContext UpdateResult.UpdateAvailable(updateInfo)
            } else {
                Log.d(TAG, "No new update available.")
                return@withContext UpdateResult.NoUpdate
            }

        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            return@withContext UpdateResult.Failure("Failed to check for updates: ${e.message}")
        }
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo): Flow<Int> = callbackFlow {
        val outputFile = File(context.filesDir, UPDATE_FILE_NAME)
        try {
            val url = URL(updateInfo.downloadUrl)
            val connection = url.openConnection()
            connection.connect()

            val fileLength = connection.contentLength
            val input = connection.getInputStream()
            val output = FileOutputStream(outputFile)
            val data = ByteArray(1024 * 4)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    trySend(progress)
                }
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()
            trySend(100) // Ensure it finishes at 100%

            Log.d(TAG, "Download complete. Verifying signature...")
            if (secureUpdateHelper.verifyLocalApkSignature(outputFile.absolutePath)) {
                Log.d(TAG, "Signature verified. Proceeding to install.")
                installApk(outputFile)
            } else {
                Log.e(TAG, "Signature verification failed!")
                outputFile.delete()
                throw Exception(context.getString(R.string.update_error_verification_failed))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            outputFile.delete()
            close(e) // Propagate error to the collector
        }
        awaitClose { /* Cleanup if needed */ }
    }.flowOn(Dispatchers.IO)

    private fun installApk(file: File) {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}