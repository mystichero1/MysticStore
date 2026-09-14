package com.mystic.store

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mystic.store.data.AppInfo
import com.mystic.store.data.AppItem
import com.mystic.store.data.AppRepository
import com.mystic.store.data.AppState
import com.mystic.store.install.AppInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Loading : UiState
    data class Content(val items: List<AppItem>) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Change this to the URL of your hosted JSON catalog. */
        const val CATALOG_URL = "https://YOUR-USERNAME.github.io/mystic-store/apps.json"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val installer = AppInstaller(application)
    private val pendingDownloads = mutableMapOf<Long, String>() // download id -> package name
    private val downloadingPackages = mutableSetOf<String>()

    private var isLoading = false

    fun refresh() {
        if (isLoading) return
        isLoading = true
        if (_uiState.value !is UiState.Content) {
            _uiState.value = UiState.Loading
        }
        viewModelScope.launch {
            try {
                val remote = AppRepository.fetchCatalog(CATALOG_URL)
                val items = remote.map(::toItem)
                _uiState.value = UiState.Content(items)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            } finally {
                isLoading = false
            }
        }
    }

    /** Re-compares installed version codes without hitting the network (e.g. after resume). */
    fun refreshInstalledState() {
        val current = _uiState.value as? UiState.Content ?: return
        _uiState.value = UiState.Content(current.items.map { toItem(it.info) })
    }

    fun onAction(item: AppItem) {
        when (item.state) {
            AppState.INSTALL, AppState.UPDATE -> startDownload(item.info)
            AppState.OPEN -> launchApp(item.info.packageName)
            AppState.DOWNLOADING -> Unit
        }
    }

    /** Called from the DownloadManager BroadcastReceiver when a download finishes. */
    fun onDownloadComplete(downloadId: Long) {
        val packageName = pendingDownloads.remove(downloadId) ?: return
        downloadingPackages.remove(packageName)
        val info = findApp(packageName) ?: return
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { installer.isDownloadSuccessful(downloadId) }
            if (ready) {
                installer.install(info)
            }
            refreshInstalledState()
        }
    }

    private fun startDownload(info: AppInfo) {
        val id = installer.download(info)
        pendingDownloads[id] = info.packageName
        downloadingPackages.add(info.packageName)
        refreshInstalledState() // flips the row to "Downloading…"
    }

    private fun launchApp(packageName: String) {
        val launchIntent = getApplication<Application>()
            .packageManager
            .getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(launchIntent)
    }

    private fun toItem(info: AppInfo): AppItem {
        val installed = installedVersionCode(info.packageName)
        val state = when {
            info.packageName in downloadingPackages -> AppState.DOWNLOADING
            installed == null -> AppState.INSTALL
            installed >= info.versionCode -> AppState.OPEN
            else -> AppState.UPDATE
        }
        return AppItem(info = info, installedVersionCode = installed, state = state)
    }

    private fun installedVersionCode(packageName: String): Long? = try {
        val pm = getApplication<Application>().packageManager
        val packageInfo: PackageInfo = pm.getPackageInfo(packageName, 0)
        PackageInfoCompat.getLongVersionCode(packageInfo)
    } catch (_: Exception) {
        null
    }

    private fun findApp(packageName: String): AppInfo? =
        (_uiState.value as? UiState.Content)
            ?.items
            ?.firstOrNull { it.info.packageName == packageName }
            ?.info
}