package com.mystic.store

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.mystic.store.data.AppItem
import com.mystic.store.ui.AppsAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AppsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != -1L) viewModel.onDownloadComplete(id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setSupportActionBar(findViewById(R.id.toolbar))

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        adapter = AppsAdapter(
            scope = lifecycleScope,
            onItemClick = ::openDetail,
            onActionClick = { item -> viewModel.onAction(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<TextInputEditText>(R.id.searchInput).doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state -> render(state) }
        }

        viewModel.refresh()
    }

    private fun openDetail(item: AppItem) {
        startActivity(AppDetailActivity.createIntent(this, item.info))
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(downloadReceiver)
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate versions when returning from the install screen or from an app.
        viewModel.refreshInstalledState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            viewModel.refresh()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun render(state: UiState) {
        when (state) {
            is UiState.Loading -> {
                progressBar.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.GONE
            }
            is UiState.Error -> {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = getString(R.string.error_loading) +
                    "\n(${state.message})"
            }
            is UiState.Content -> {
                progressBar.visibility = View.GONE
                adapter.submit(state.items)
                if (state.items.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    emptyView.setText(
                        if (viewModel.hasActiveSearch()) R.string.no_results
                        else R.string.empty_catalog
                    )
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
}