package com.mystic.store.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object AppRepository {

    suspend fun fetchCatalog(catalogUrl: String): List<AppInfo> = withContext(Dispatchers.IO) {
        val connection = URL(catalogUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Server returned HTTP $code")
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            AppJsonParser.parse(raw)
        } finally {
            connection.disconnect()
        }
    }
}