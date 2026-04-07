package com.metrolist.music.utils

import com.metrolist.shared.hifi.DEFAULT_HIFI_API_URL
import com.metrolist.shared.hifi.HifiApiClient
import com.metrolist.shared.hifi.HifiQuality
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import timber.log.Timber

object HifiApi {
    private const val TAG = "HifiApi"

    private val client by lazy {
        HifiApiClient(
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        )
    }

    fun searchTrack(title: String, author: String, baseUrl: String = DEFAULT_HIFI_API_URL): String? {
        Timber.tag(TAG).d("Searching track on HiFi API: $title - $author")
        return runBlocking {
            runCatching { client.searchTrack(title, author, baseUrl) }
                .onFailure { Timber.tag(TAG).e(it, "Error searching track") }
                .getOrNull()
        }
    }

    fun getStreamUrl(trackId: String, quality: String, baseUrl: String = DEFAULT_HIFI_API_URL): String? {
        val requestedQuality = HifiQuality.entries.firstOrNull { it.value == quality } ?: return null
        Timber.tag(TAG).d("Getting stream URL for track ID $trackId with quality $quality")
        return runBlocking {
            runCatching { client.getStreamUrl(trackId, requestedQuality, baseUrl) }
                .onFailure { Timber.tag(TAG).e(it, "Error getting stream URL") }
                .getOrNull()
        }
    }
}
