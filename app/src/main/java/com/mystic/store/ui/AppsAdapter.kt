package com.mystic.store.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.mystic.store.R
import com.mystic.store.data.AppItem
import com.mystic.store.data.AppState
import kotlinx.coroutines.CoroutineScope

class AppsAdapter(
    private val scope: CoroutineScope,
    private val onItemClick: (AppItem) -> Unit,
    private val onActionClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppItem>()

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

        private val card: MaterialCardView = itemView as MaterialCardView
        private val iconView: ImageView = itemView.findViewById(R.id.iconView)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val actionButton: Button = itemView.findViewById(R.id.actionButton)

        fun bind(item: AppItem) {
            val info = item.info
            nameText.text = info.name
            metaText.text = itemView.context.getString(
                R.string.version_x,
                info.packageName,
                info.versionName
            )
            descriptionText.text = info.description.orEmpty()
            ImageLoader.load(info.iconUrl, iconView, scope)

            val res = actionButton.resources
            actionButton.isEnabled = item.state != AppState.DOWNLOADING
            actionButton.text = when (item.state) {
                AppState.INSTALL -> res.getString(R.string.install)
                AppState.UPDATE -> res.getString(R.string.update)
                AppState.OPEN -> res.getString(R.string.open)
                AppState.DOWNLOADING -> res.getString(R.string.downloading)
            }

            card.setOnClickListener { onItemClick(item) }
            actionButton.setOnClickListener { onActionClick(item) }
        }
    }
}