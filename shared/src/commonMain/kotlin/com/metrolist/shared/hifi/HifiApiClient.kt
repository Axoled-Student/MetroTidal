package com.metrolist.shared.hifi

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val DEFAULT_HIFI_API_URL = "https://api.monochrome.tf"

enum class HifiQuality(val value: String) {
    HI_RES_LOSSLESS("HI_RES_LOSSLESS"),
    LOSSLESS("LOSSLESS"),
    HIGH("HIGH"),
    LOW("LOW"),
}

class HifiApiClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun searchTrack(
        title: String,
        author: String,
        baseUrl: String = DEFAULT_HIFI_API_URL,
    ): String? = runCatching {
        val responseBody = client.get("$baseUrl/search/") {
            parameter("s", "$title $author")
            parameter("limit", 1)
        }.bodyAsText()

        val firstItem = json.parseToJsonElement(responseBody)
            .jsonObject["data"]
            ?.jsonObject
            ?.get("items")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject

        firstItem?.get("id")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    suspend fun getStreamUrl(
        trackId: String,
        quality: HifiQuality,
        baseUrl: String = DEFAULT_HIFI_API_URL,
    ): String? {
        val requestedUrl = fetchStreamUrl(trackId, quality, baseUrl)
        if (requestedUrl != null || quality != HifiQuality.HI_RES_LOSSLESS) {
            return requestedUrl
        }

        return fetchStreamUrl(trackId, HifiQuality.LOSSLESS, baseUrl)
    }

    private suspend fun fetchStreamUrl(
        trackId: String,
        quality: HifiQuality,
        baseUrl: String,
    ): String? = runCatching {
        val responseBody = client.get("$baseUrl/track/") {
            parameter("id", trackId)
            parameter("quality", quality.value)
        }.bodyAsText()

        val data = json.parseToJsonElement(responseBody)
            .jsonObject["data"]
            ?.jsonObject
            ?: return null

        val manifestMimeType = data["manifestMimeType"]?.jsonPrimitive?.contentOrNull ?: return null
        val encodedManifest = data["manifest"]?.jsonPrimitive?.contentOrNull ?: return null
        val decodedManifest = decodeManifest(encodedManifest) ?: return null

        when (manifestMimeType) {
            "application/vnd.tidal.bts" -> {
                json.parseToJsonElement(decodedManifest)
                    .jsonObject["urls"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonPrimitive
                    ?.contentOrNull
            }

            "application/dash+xml" -> parseDashManifest(decodedManifest)
            else -> null
        }
    }.getOrNull()

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeManifest(manifest: String): String? = runCatching {
        Base64.Default.decode(manifest).decodeToString()
    }.getOrNull()

    private fun parseDashManifest(manifest: String): String? {
        return BASE_URL_REGEX.find(manifest)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        val BASE_URL_REGEX = Regex(
            pattern = "<BaseURL>([\\s\\S]*?)</BaseURL>",
            options = setOf(RegexOption.IGNORE_CASE),
        )
    }
}
