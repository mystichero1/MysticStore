package com.mystic.store.install

import android.content.Context
import com.mystic.store.data.AppInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-live tracker for APK downloads so the list and the detail screen
 * (two activities) agree on what is currently downloading.
 */
object DownloadTracker {

    private val pending = ConcurrentHashMap<Long, AppInfo>() // download id -> app
    private val downloadingPackages = ConcurrentHashMap<String, Boolean>()

    fun enqueue(context: Context, info: AppInfo): Long {
        val id = AppInstaller(context).download(info)
        pending[id] = info
        downloadingPackages[info.packageName] = true
        return id
    }

    fun isDownloading(packageName: String): Boolean =
        downloadingPackages.containsKey(packageName)

    /** Removes the finished download; installs the APK if it succeeded. */
    fun onDownloadComplete(context: Context, downloadId: Long) {
        val info = pending.remove(downloadId) ?: return
        downloadingPackages.remove(info.packageName)
        val installer = AppInstaller(context)
        if (installer.isDownloadSuccessful(downloadId)) {
            installer.install(info)
        }
    }
}