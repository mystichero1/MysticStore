package com.mystic.store

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.mystic.store.data.AppInfo
import com.mystic.store.data.AppState
import com.mystic.store.data.ReadmeLoader
import com.mystic.store.data.VersionComparator
import com.mystic.store.install.DownloadTracker
import com.mystic.store.install.PackageUtils
import com.mystic.store.ui.ImageLoader
import com.mystic.store.ui.Markdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_PACKAGE = "extra_package"
        private const val EXTRA_VERSION = "extra_version"
        private const val EXTRA_APK_URL = "extra_apk_url"
        private const val EXTRA_ICON_URL = "extra_icon_url"
        private const val EXTRA_DESCRIPTION = "extra_description"
        private const val EXTRA_REPO = "extra_repo"

        fun createIntent(context: Context, info: AppInfo): Intent =
            Intent(context, AppDetailActivity::class.java).apply {
                putExtra(EXTRA_NAME, info.name)
                putExtra(EXTRA_PACKAGE, info.packageName)
                putExtra(EXTRA_VERSION, info.versionName)
                putExtra(EXTRA_APK_URL, info.apkUrl)
                putExtra(EXTRA_ICON_URL, info.iconUrl)
                putExtra(EXTRA_DESCRIPTION, info.description)
                putExtra(EXTRA_REPO, info.repo)
            }
    }

    private lateinit var info: AppInfo
    private lateinit var actionButton: Button

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != -1L) {
                    DownloadTracker.onDownloadComplete(this@AppDetailActivity, id)
                    renderState()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        info = AppInfo(
            name = intent.getStringExtra(EXTRA_NAME) ?: "Unknown app",
            packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: "",
            versionName = intent.getStringExtra(EXTRA_VERSION) ?: "",
            apkUrl = intent.getStringExtra(EXTRA_APK_URL) ?: "",
            iconUrl = intent.getStringExtra(EXTRA_ICON_URL),
            description = intent.getStringExtra(EXTRA_DESCRIPTION),
            repo = intent.getStringExtra(EXTRA_REPO)
        )

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = info.name

        findViewById<TextView>(R.id.nameText).text = info.name
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.version_x_detail, info.versionName)
        findViewById<TextView>(R.id.packageText).text =
            getString(R.string.package_x, info.packageName)

        ImageLoader.load(
            info.iconUrl,
            findViewById(R.id.iconView),
            lifecycleScope
        )

        actionButton = findViewById(R.id.actionButton)
        actionButton.setOnClickListener {
            when (currentState()) {
                AppState.INSTALL, AppState.UPDATE -> {
                    DownloadTracker.enqueue(this, info)
                    renderState()
                }
                AppState.OPEN -> launchApp(info.packageName)
                AppState.DOWNLOADING -> Unit
            }
        }

        loadInfo()
    }

    private fun loadInfo() {
        val descriptionText = findViewById<TextView>(R.id.descriptionText)
        val readmeHeading = findViewById<TextView>(R.id.readmeHeading)
        val readmeProgress = findViewById<ProgressBar>(R.id.readmeProgress)

        val repo = info.repo
        if (repo == null) {
            // No repo link: fall back to the catalog description if any.
            descriptionText.setTextColor(resources.getColor(R.color.textSecondary, null))
            descriptionText.text = info.description ?: getString(R.string.no_description)
            readmeHeading.visibility = View.GONE
            readmeProgress.visibility = View.GONE
            return
        }

        readmeHeading.visibility = View.VISIBLE
        readmeProgress.visibility = View.VISIBLE
        descriptionText.visibility = View.GONE

        lifecycleScope.launch {
            val markdown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ReadmeLoader.fetch(repo)
            }
            readmeProgress.visibility = View.GONE
            descriptionText.visibility = View.VISIBLE
            if (markdown == null) {
                descriptionText.setText(R.string.readme_error)
            } else {
                val html = Markdown.toHtml(markdown, readmeBaseUrl())
                val imageGetter = TextViewImageGetter(
                    view = descriptionText,
                    scope = lifecycleScope,
                    rawHtml = html,
                    placeholder = ContextCompat.getDrawable(
                        this@AppDetailActivity,
                        R.drawable.ic_app_placeholder
                    )
                )
                descriptionText.text = HtmlCompat.fromHtml(
                    html,
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                    imageGetter,
                    null
                )
                descriptionText.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    /** Base URL for resolving relative image paths in the README. */
    private fun readmeBaseUrl(): String? {
        val repo = info.repo ?: return null
        return if (repo.startsWith("http://") || repo.startsWith("https://")) {
            val trimmed = repo.trimEnd('/')
            val idx = trimmed.lastIndexOf('/')
            if (idx in 1 until trimmed.length) trimmed.substring(0, idx + 1) else "$trimmed/"
        } else {
            "https://raw.githubusercontent.com/$repo/HEAD/"
        }
    }

    /**
     * Fetches README images in the background. Returns a placeholder immediately,
     * then re-renders the TextView from the raw HTML once the real bitmap is in,
     * so cache hits replace the placeholders and get their real size/layout.
     */
    private class TextViewImageGetter(
        private val view: TextView,
        private val scope: CoroutineScope,
        private val rawHtml: String,
        placeholder: Drawable?
    ) : Html.ImageGetter {

        private val cache = HashMap<String, Drawable>()
        private val inFlight = HashSet<String>()
        private val failed = HashSet<String>()
        private val density = view.context.resources.displayMetrics.density
        private val maxWidth = (view.context.resources.displayMetrics.widthPixels * 0.92f).toInt()

        private val placeholder: Drawable = (placeholder ?: ContextCompat.getDrawable(
            view.context,
            R.drawable.ic_app_placeholder
        )!!).apply {
            val h = (maxWidth * 0.5f).toInt()
            setBounds(0, 0, maxWidth, h)
        }

        override fun getDrawable(source: String): Drawable {
            cache[source]?.let { return it }
            if (inFlight.contains(source) || failed.contains(source)) {
                return placeholder
            }
            inFlight.add(source)
            val src = ImageLoader.normalize(source)
            scope.launch {
                val bitmap = ImageLoader.fetchBitmap(src)
                if (bitmap != null) {
                    cache[source] = BitmapDrawable(view.context.resources, scaleToWidth(bitmap)).apply {
                        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                    }
                } else {
                    failed.add(source)
                }
                inFlight.remove(source)
                reRender()
            }
            return placeholder
        }

        private fun reRender() {
            view.text = HtmlCompat.fromHtml(
                rawHtml,
                HtmlCompat.FROM_HTML_MODE_COMPACT,
                this,
                null
            )
        }

        private fun scaleToWidth(bitmap: Bitmap): Bitmap {
            if (bitmap.width <= maxWidth) return bitmap
            val height = (bitmap.height.toFloat() * maxWidth / bitmap.width).toInt().coerceAtLeast(1)
            return if (height == bitmap.height) bitmap
            else Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
        }
    }

    private fun currentState(): AppState {
        val installed = PackageUtils.installedVersionName(this, info.packageName)
        return when {
            DownloadTracker.isDownloading(info.packageName) -> AppState.DOWNLOADING
            installed == null -> AppState.INSTALL
            VersionComparator.isOutdated(installed, info.versionName) -> AppState.UPDATE
            else -> AppState.OPEN
        }
    }

    private fun renderState() {
        actionButton.isEnabled = currentState() != AppState.DOWNLOADING
        actionButton.text = when (currentState()) {
            AppState.INSTALL -> getString(R.string.install)
            AppState.UPDATE -> getString(R.string.update)
            AppState.OPEN -> getString(R.string.open)
            AppState.DOWNLOADING -> getString(R.string.downloading)
        }
    }

    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
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
        renderState()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}