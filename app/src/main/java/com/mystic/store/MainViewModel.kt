package com.mystic.store

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mystic.store.data.AppInfo
import com.mystic.store.data.AppItem
import com.mystic.store.data.AppRepository
import com.mystic.store.data.AppState
import com.mystic.store.data.VersionComparator
import com.mystic.store.install.DownloadTracker
import com.mystic.store.install.PackageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data class Content(val items: List<AppItem>) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** URL of the hosted JSON catalog (your GitHub Pages site). */
        const val CATALOG_URL = "https://mystichero1.github.io/MysticStore/apps.json"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var remoteInfo: List<AppInfo> = emptyList()
    private var searchQuery: String = ""
    private var isLoading = false

    fun refresh() {
        if (isLoading) return
        isLoading = true
        if (_uiState.value !is UiState.Content) {
            _uiState.value = UiState.Loading
        }
        viewModelScope.launch {
            try {
                remoteInfo = AppRepository.fetchCatalog(CATALOG_URL)
                publish()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            } finally {
                isLoading = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query.trim()
        publish()
    }

    fun hasActiveSearch(): Boolean = searchQuery.isNotEmpty()

    /** Re-compares installed version codes without hitting the network (e.g. after resume). */
    fun refreshInstalledState() {
        publish()
    }

    fun onAction(item: AppItem) {
        when (item.state) {
            AppState.INSTALL, AppState.UPDATE -> {
                DownloadTracker.enqueue(getApplication(), item.info)
                publish()
            }
            AppState.OPEN -> launchApp(item.info.packageName)
            AppState.DOWNLOADING -> Unit
        }
    }

    /** Called from the DownloadManager BroadcastReceiver when a download finishes. */
    fun onDownloadComplete(downloadId: Long) {
        DownloadTracker.onDownloadComplete(getApplication(), downloadId)
        publish()
    }

    private fun publish() {
        val allItems = remoteInfo.map(::toItem)
        val items = if (searchQuery.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                it.info.name.contains(searchQuery, ignoreCase = true) ||
                    it.info.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        _uiState.value = UiState.Content(items)
    }

    private fun toItem(info: AppInfo): AppItem {
        val installed = PackageUtils.installedVersionName(getApplication(), info.packageName)
        val state = when {
            DownloadTracker.isDownloading(info.packageName) -> AppState.DOWNLOADING
            installed == null -> AppState.INSTALL
            VersionComparator.isOutdated(installed, info.versionName) -> AppState.UPDATE
            else -> AppState.OPEN
        }
        return AppItem(info = info, installedVersionName = installed, state = state)
    }

    private fun launchApp(packageName: String) {
        val launchIntent = getApplication<Application>()
            .packageManager
            .getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(launchIntent)
    }
}