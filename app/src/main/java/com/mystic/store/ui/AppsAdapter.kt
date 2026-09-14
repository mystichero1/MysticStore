package com.mystic.store.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mystic.store.R
import com.mystic.store.data.AppItem
import com.mystic.store.data.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AppsAdapter(
    private val scope: CoroutineScope,
    private val onActionClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppItem>()

    private val iconCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun submit(list: List<AppItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val iconView: ImageView = itemView.findViewById(R.id.iconView)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)
        private val actionButton: Button = itemView.findViewById(R.id.actionButton)

        fun bind(item: AppItem) {
            val info = item.info
            nameText.text = info.name
            metaText.text = itemView.context.getString(
                R.string.version_x,
                info.packageName,
                info.versionName
            )
            loadIcon(info.iconUrl, iconView)

            val res = actionButton.resources
            actionButton.isEnabled = item.state != AppState.DOWNLOADING
            actionButton.text = when (item.state) {
                AppState.INSTALL -> res.getString(R.string.install)
                AppState.UPDATE -> res.getString(R.string.update)
                AppState.OPEN -> res.getString(R.string.open)
                AppState.DOWNLOADING -> res.getString(R.string.downloading)
            }
            actionButton.setOnClickListener { onActionClick(item) }
        }
    }

    private fun loadIcon(url: String?, imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_app_placeholder)
        if (url == null) return

        iconCache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        scope.launch {
            fetchBitmap(url)?.let { bitmap ->
                iconCache.put(url, bitmap)
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }
}