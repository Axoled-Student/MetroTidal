package com.metrolist.shared.hifi

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

class HifiApiClientTest {
    @Test
    fun searchTrackReturnsFirstTrackId() = runBlocking {
        val client = HifiApiClient(
            HttpClient(
                MockEngine { request ->
                    assertEquals("/search/", request.url.encodedPath)
                    assertEquals("Track Artist", request.url.parameters["s"])
                    jsonResponse(
                        """
                        {"data":{"items":[{"id":12345},{"id":67890}]}}
                        """.trimIndent(),
                    )
                },
            ),
        )

        val trackId = client.searchTrack("Track", "Artist")

        assertEquals("12345", trackId)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun getStreamUrlFallsBackFromHiResToLossless() = runBlocking {
        val requestedQualities = mutableListOf<String>()
        val hiResManifest = Base64.Default.encode("<MPD></MPD>".encodeToByteArray())
        val losslessManifest = Base64.Default.encode(
            """
            {"urls":["https://example.com/audio.flac"]}
            """.trimIndent().encodeToByteArray(),
        )

        val client = HifiApiClient(
            HttpClient(
                MockEngine { request ->
                    requestedQualities += request.url.parameters["quality"].orEmpty()
                    when (request.url.parameters["quality"]) {
                        HifiQuality.HI_RES_LOSSLESS.value -> {
                            jsonResponse(trackResponse(hiResManifest, "application/dash+xml"))
                        }

                        HifiQuality.LOSSLESS.value -> {
                            jsonResponse(trackResponse(losslessManifest, "application/vnd.tidal.bts"))
                        }

                        else -> error("Unexpected request: ${request.url}")
                    }
                },
            ),
        )

        val streamUrl = client.getStreamUrl("12345", HifiQuality.HI_RES_LOSSLESS)

        assertEquals(listOf("HI_RES_LOSSLESS", "LOSSLESS"), requestedQualities)
        assertEquals("https://example.com/audio.flac", streamUrl)
    }

    private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun trackResponse(manifest: String, mimeType: String): String {
        return """
            {"data":{"manifest":"$manifest","manifestMimeType":"$mimeType"}}
        """.trimIndent()
    }
}
