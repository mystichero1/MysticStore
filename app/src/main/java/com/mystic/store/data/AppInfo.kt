package com.mystic.store.data

import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val apkUrl: String,
    val iconUrl: String?,
    val description: String?,
    val repo: String?
)

/**
 * Compares plain-dotted version names like "1.0", "1.10.2" numerically per segment,
 * so "1.10" is correctly newer than "1.9". A leading "v" is ignored.
 */
object VersionComparator {

    fun isOutdated(installed: String, remote: String): Boolean = compare(installed, remote) < 0

    fun compare(a: String, b: String): Int {
        val aParts = a.trim('v', 'V').split('.')
        val bParts = b.trim('v', 'V').split('.')
        val maxParts = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxParts) {
            val cmp = comparePart(aParts.getOrNull(i) ?: "0", bParts.getOrNull(i) ?: "0")
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun comparePart(a: String, b: String): Int {
        val aNum = a.toLongOrNull()
        val bNum = b.toLongOrNull()
        return when {
            aNum != null && bNum != null -> aNum.compareTo(bNum)
            else -> a.compareTo(b)
        }
    }
}

enum class AppState {
    INSTALL,
    UPDATE,
    OPEN,
    DOWNLOADING
}

data class AppItem(
    val info: AppInfo,
    val installedVersionName: String?,
    val state: AppState
)

object AppJsonParser {

    private const val KEY_APPS = "apps"
    private const val KEY_NAME = "name"
    private const val KEY_PACKAGE = "package"
    private const val KEY_VERSION_NAME = "versionName"
    private const val KEY_APK_URL = "apkUrl"
    private const val KEY_ICON_URL = "iconUrl"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_REPO = "repo"

    /**
     * Expected shape of the hosted JSON:
     * {
     *   "apps": [
     *     {
     *       "name": "My App",
     *       "package": "com.example.myapp",
     *       "versionName": "1.1",
     *       "apkUrl": "https://example.com/app-v1.1.apk",
     *       "iconUrl": "https://example.com/icon.png",
     *       "description": "Short summary shown in the list.",
     *       "repo": "mystichero1/MyApp"
     *     }
     *   ]
     * }
     * "versionName" is compared numerically to decide Install/Update/Open, so bump it
     * (1.0 -> 1.1) on every release. "repo" is optional but recommended: the detail
     * screen then shows the app's README.md from GitHub. Malformed entries are
     * skipped so one bad row never breaks the list.
     */
    fun parse(raw: String): List<AppInfo> {
        val root = JSONObject(cleanJson(raw))
        val array = root.optJSONArray(KEY_APPS) ?: return emptyList()
        val apps = mutableListOf<AppInfo>()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { parseApp(it)?.let(apps::add) }
        }
        return apps
    }

    /**
     * Fixes the two most common hand-typing mistakes so a sloppy file still loads:
     * trailing commas before }/], and a missing comma between a string value and
     * the next "key" on its own line.
     */
    internal fun cleanJson(raw: String): String {
        var s = raw
        s = Regex(",\\s*([}\\]])").replace(s, "$1")
        s = Regex("\"\\r?\\n\\s*(\")").replace(s, "\",\n$1")
        return s
    }

    private fun parseApp(obj: JSONObject): AppInfo? = try {
        AppInfo(
            name = obj.getString(KEY_NAME),
            packageName = obj.getString(KEY_PACKAGE),
            versionName = obj.getString(KEY_VERSION_NAME),
            apkUrl = obj.getString(KEY_APK_URL),
            iconUrl = obj.optString(KEY_ICON_URL).takeIf { it.isNotBlank() },
            description = obj.optString(KEY_DESCRIPTION).takeIf { it.isNotBlank() },
            repo = obj.optString(KEY_REPO).takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) {
        null
    }
}