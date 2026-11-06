package net.theluckycoder.familyphotos.core.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.core.data.di.NetworkModule
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads the app's latest APK from the server and hands it to the system installer. */
@Singleton
class AppUpdateRepository @Inject internal constructor(
    @param:ApplicationContext
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private val _latestVersion = MutableStateFlow<Int?>(null)

    /**
     * Newest app version code announced by the server in its sync responses, null until a sync
     * announces one. Kept in memory only: each app start relies on the next sync.
     */
    val latestVersion: StateFlow<Int?> = _latestVersion.asStateFlow()

    fun setLatestVersion(versionCode: Int) {
        _latestVersion.value = versionCode
    }

    private val directory: File
        get() = File(context.cacheDir, DIRECTORY_NAME)

    private val apkFile: File
        get() = File(directory, APK_NAME)

    /**
     * Returns an APK of at least [versionCode], downloading it unless a previous download is still
     * valid (e.g. the user backed out of the installer). [onProgress] gets 0..1, or null while the
     * size is unknown. Null if the download failed or the file isn't an update of this app.
     */
    suspend fun downloadApk(versionCode: Int, onProgress: (Float?) -> Unit): File? =
        withContext(Dispatchers.IO) {
            if (isValidUpdate(apkFile, versionCode)) return@withContext apkFile

            directory.mkdirs()
            val partFile = File(directory, "$APK_NAME.part")
            try {
                val request = Request.Builder()
                    .url("${NetworkModule.PLACEHOLDER_BASE_URL}api/app/download")
                    // Compression would hide Content-Length (and an APK is already compressed)
                    .header("Accept-Encoding", "identity")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "APK download failed: HTTP ${response.code}")
                        return@withContext null
                    }

                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0 }
                    var downloaded = 0L
                    var lastPercent = -1
                    onProgress(if (total != null) 0f else null)

                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total != null) {
                                    val percent = (downloaded * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isValidUpdate(partFile, versionCode) || !partFile.renameTo(apkFile)) {
                    Log.e(TAG, "The downloaded APK isn't an update of this app")
                    return@withContext null
                }
                apkFile
            } catch (e: IOException) {
                Log.e(TAG, "APK download failed", e)
                null
            } finally {
                partFile.delete()
            }
        }

    /** The system installer for [apk]. Asks for "Install unknown apps" the first time. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    suspend fun deleteDownloadedApk() = withContext(Dispatchers.IO) {
        directory.deleteRecursively()
    }

    /** Same package (so same signing key is checked by the installer) and new enough. */
    private fun isValidUpdate(file: File, versionCode: Int): Boolean {
        if (!file.isFile) return false
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        return info.packageName == context.packageName && info.longVersionCode >= versionCode
    }

    private companion object {
        const val TAG = "AppUpdateRepository"
        const val DIRECTORY_NAME = "updates"
        const val APK_NAME = "FamilyPhotos.apk"
    }
}
