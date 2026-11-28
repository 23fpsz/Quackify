package com.quackify.app.lastfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

class LastFmApiException(
    val errorCode: Int?,
    message: String
) : Exception(message)

class LastFmApiClient(
    private val apiKey: String,
    private val sharedSecret: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val API_URL = "https://ws.audioscrobbler.com/2.0/"
    }

    init {
        require(apiKey.isNotBlank()) { "Last.fm API Key is missing" }
        require(sharedSecret.isNotBlank()) { "Last.fm Shared Secret is missing" }
    }

    suspend fun call(
        method: String,
        parameters: Map<String, String> = emptyMap()
    ): JSONObject = withContext(Dispatchers.IO) {
        require(method.isNotBlank()) { "API method must not be blank" }

        val signedParameters = LinkedHashMap<String, String>()
        signedParameters["api_key"] = apiKey
        signedParameters["method"] = method
        signedParameters.putAll(parameters)

        require("api_sig" !in signedParameters) {
            "api_sig is generated internally"
        }
        require("format" !in signedParameters) {
            "format is managed internally"
        }

        val signature = createSignature(signedParameters)

        val form = FormBody.Builder().apply {
            signedParameters.forEach { (key, value) ->
                add(key, value)
            }
            add("api_sig", signature)
            add("format", "json")
        }.build()

        val request = Request.Builder()
            .url(API_URL)
            .post(form)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string().ifBlank {
                throw LastFmApiException(
                    null,
                    "Last.fm returned an empty response"
                )
            }

            if (!response.isSuccessful) {
                throw LastFmApiException(
                    response.code,
                    "Last.fm HTTP request failed (${response.code})"
                )
            }

            val json = try {
                JSONObject(responseBody)
            } catch (_: Exception) {
                throw LastFmApiException(
                    null,
                    "Last.fm returned an invalid JSON response"
                )
            }

            if (json.has("error")) {
                throw LastFmApiException(
                    json.optInt("error"),
                    json.optString("message", "Last.fm API error")
                )
            }

            json
        }
    }

    private fun createSignature(
        parameters: Map<String, String>
    ): String {
        val signatureInput = buildString {
            parameters.toSortedMap().forEach { (key, value) ->
                append(key)
                append(value)
            }
            append(sharedSecret)
        }

        val digest = MessageDigest.getInstance("MD5")
            .digest(signatureInput.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
