package com.mystic.store.install

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.mystic.store.data.AppInfo
import java.io.File

class AppInstaller(private val context: Context) {

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Queues the APK download and returns the DownloadManager request id. */
    fun download(info: AppInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle(info.name)
            setDescription("${info.versionName} (${info.versionCode})")
            setMimeType(MIME_APK)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                destinationFile(info).name
            )
        }
        return downloadManager.enqueue(request)
    }

    /** The app-specific Download directory where DownloadManager writes the APK. */
    fun destinationFile(info: AppInfo): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return File(baseDir, safeFileName(info.name) + "-" + info.versionCode + ".apk")
    }

    /** Queries DownloadManager to confirm a finished download actually succeeded. */
    fun isDownloadSuccessful(downloadId: Long): Boolean {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        return try {
            if (cursor == null || !cursor.moveToFirst()) return false
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return false
            cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
        } finally {
            cursor?.close()
        }
    }

    /**
     * Opens the system APK installer for the downloaded file by sharing it through
     * a FileProvider content URI. Returns false if the file is missing or no
     * activity can handle the intent.
     */
    fun install(info: AppInfo): Boolean {
        val file = destinationFile(info)
        if (!file.exists()) return false

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val MIME_APK = "application/vnd.android.package-archive"
    }
}