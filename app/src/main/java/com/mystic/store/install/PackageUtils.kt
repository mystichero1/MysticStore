package com.mystic.store.install

import android.content.Context

object PackageUtils {

    /** Installed versionName for a package, or null when it isn't installed. */
    fun installedVersionName(context: Context, packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: Exception) {
        null
    }
}