package com.mystic.store.data

import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val iconUrl: String?
)

enum class AppState {
    INSTALL,
    UPDATE,
    OPEN,
    DOWNLOADING
}

data class AppItem(
    val info: AppInfo,
    val installedVersionCode: Long?,
    val state: AppState
)

object AppJsonParser {

    private const val KEY_APPS = "apps"
    private const val KEY_NAME = "name"
    private const val KEY_PACKAGE = "package"
    private const val KEY_VERSION_CODE = "versionCode"
    private const val KEY_VERSION_NAME = "versionName"
    private const val KEY_APK_URL = "apkUrl"
    private const val KEY_ICON_URL = "iconUrl"

    /**
     * Expected shape of the hosted JSON:
     * {
     *   "apps": [
     *     {
     *       "name": "My App",
     *       "package": "com.example.myapp",
     *       "versionCode": 2,
     *       "versionName": "1.1",
     *       "apkUrl": "https://example.com/app-v1.1.apk",
     *       "iconUrl": "https://example.com/icon.png"
     *     }
     *   ]
     * }
     * Malformed entries are skipped so one bad row never breaks the list.
     */
    fun parse(raw: String): List<AppInfo> {
        val root = JSONObject(raw)
        val array = root.optJSONArray(KEY_APPS) ?: return emptyList()
        val apps = mutableListOf<AppInfo>()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { parseApp(it)?.let(apps::add) }
        }
        return apps
    }

    private fun parseApp(obj: JSONObject): AppInfo? = try {
        AppInfo(
            name = obj.getString(KEY_NAME),
            packageName = obj.getString(KEY_PACKAGE),
            versionCode = obj.getLong(KEY_VERSION_CODE),
            versionName = obj.getString(KEY_VERSION_NAME),
            apkUrl = obj.getString(KEY_APK_URL),
            iconUrl = obj.optString(KEY_ICON_URL).takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) {
        null
    }
}