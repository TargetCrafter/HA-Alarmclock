package com.targetcrafter.haalarmclock.ha

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val TAG = "HaApiClient"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Pushes alarm/ringing state to the HA Alarm Clock custom integration's REST endpoint. */
class HaApiClient(private val httpClient: OkHttpClient) {

    /** Returns whether the push succeeded. Never throws: a failed sync is not worth taking the app
     * down for, and it used to be able to. Building the request was outside the try — and
     * `Request.Builder.url` throws [IllegalArgumentException] on a malformed base URL, which the
     * user types by hand — while the catch only covered [IOException], so anything else OkHttp
     * raised escaped into the caller's coroutine, which has no exception handler. That crashes the
     * process, and this runs again on every state change and every service start, so it would
     * crash repeatedly. Repeated crashes get an app force-stopped, and a force-stop cancels every
     * alarm it had scheduled. */
    suspend fun pushSync(baseUrl: String, accessToken: String, payload: JsonObject): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${normalizeBaseUrl(baseUrl)}/api/ha_alarmclock/sync")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                httpClient.newCall(request).execute().use { it.isSuccessful }
            } catch (e: IOException) {
                Log.w(TAG, "Sync push failed", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Sync push failed unexpectedly — check the Home Assistant URL", e)
                false
            }
        }
}

internal fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')
