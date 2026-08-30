package com.targetcrafter.haalarmclock.ha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Pushes alarm/ringing state to the HA Alarm Clock custom integration's REST endpoint. */
class HaApiClient(private val httpClient: OkHttpClient) {

    suspend fun pushSync(baseUrl: String, accessToken: String, payload: JsonObject): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${normalizeBaseUrl(baseUrl)}/api/ha_alarmclock/sync")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                httpClient.newCall(request).execute().use { it.isSuccessful }
            } catch (_: IOException) {
                false
            }
        }
}

internal fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')
