package com.mystic.store.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a repo's README markdown. Accepts either a short "owner/repo" form
 * (resolved via the GitHub API) or a full http(s) URL to a raw markdown file.
 */
object ReadmeLoader {

    suspend fun fetch(repo: String): String? = withContext(Dispatchers.IO) {
        try {
            val directUrl = repo.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val source: String =
                if (directUrl != null) directUrl
                else "https://api.github.com/repos/$repo/readme"

            val connection = URL(source).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "MysticStore")
                if (connection.responseCode !in 200..299) return@withContext null

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (directUrl != null) {
                    body
                } else {
                    val content = JSONObject(body).getString("content")
                    val decoded = Base64.decode(
                        content.filterNot { it == '\n' || it == '\r' },
                        Base64.DEFAULT
                    )
                    String(decoded, Charsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}