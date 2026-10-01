package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.njarasoa.fijerena.core.player.network.NetworkModule

/** The sync server failed a request; [status] is the HTTP status, 0 when it was never reached. */
class SyncApiException(
    val status: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        /** Not an HTTP status: the server answered, but isn't a compatible Fijerena sync server. */
        const val INCOMPATIBLE = -1
    }
}

/**
 * HTTP and WebSocket client of the sync server (protocol 1 — see `server/README.md`). Stateless:
 * the caller passes the server URL and device token.
 */
class SyncApi(
    private val client: OkHttpClient = NetworkModule.okHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    suspend fun info(serverUrl: String): SyncWire.Info = call(serverUrl, "GET", "/info")

    suspend fun createAccount(
        serverUrl: String,
        deviceName: String,
        setupSecret: String?,
    ): SyncWire.DeviceCredentials =
        call(
            serverUrl,
            "POST",
            "/accounts",
            body = """{"deviceName":${json.encodeToString(deviceName)}}""",
            headers = setupSecret?.let { mapOf("X-Setup-Secret" to it) }.orEmpty(),
        )

    suspend fun createPairing(
        serverUrl: String,
        token: String,
    ): SyncWire.Pairing = call(serverUrl, "POST", "/pairings", token = token, body = "{}")

    suspend fun pair(
        serverUrl: String,
        code: String,
        deviceName: String,
    ): SyncWire.DeviceCredentials =
        call(
            serverUrl,
            "POST",
            "/pair",
            body = """{"code":${json.encodeToString(code)},"deviceName":${json.encodeToString(deviceName)}}""",
        )

    suspend fun push(
        serverUrl: String,
        token: String,
        records: List<SyncWire.Record>,
    ): SyncWire.PushResponse =
        call(serverUrl, "POST", "/changes", token = token, body = json.encodeToString(SyncWire.PushRequest(records)))

    /** A page of records after [since]; `resync` set when [since] is too old to continue from. */
    suspend fun pull(
        serverUrl: String,
        token: String,
        since: Long,
        limit: Int = 500,
    ): SyncWire.PullResponse = call(serverUrl, "GET", "/changes?since=$since&limit=$limit", token = token, acceptGone = true)

    suspend fun devices(
        serverUrl: String,
        token: String,
    ): List<SyncWire.Device> = call<SyncWire.Devices>(serverUrl, "GET", "/devices", token = token).devices

    suspend fun revoke(
        serverUrl: String,
        token: String,
        deviceId: String,
    ): Unit = call<SyncWire.Revoked>(serverUrl, "DELETE", "/devices/$deviceId", token = token).let { }

    /** A handoff for this (unlinked) device to be given an account through — see `server/src/handoff.ts`. */
    suspend fun openHandoff(serverUrl: String): SyncWire.HandoffOpened = call(serverUrl, "POST", "/handoffs", body = "{}")

    suspend fun collectHandoff(
        serverUrl: String,
        handoffId: String,
    ): SyncWire.HandoffCollected = call(serverUrl, "GET", "/handoffs/$handoffId")

    suspend fun fillHandoff(
        serverUrl: String,
        token: String,
        handoffId: String,
        sealed: String,
        senderKey: String,
    ): Unit =
        call<SyncWire.HandoffFilled>(
            serverUrl,
            "POST",
            "/handoffs/$handoffId",
            token = token,
            body = json.encodeToString(SyncWire.HandoffFill(sealed, senderKey)),
        ).let { }

    fun openSocket(
        serverUrl: String,
        token: String,
        listener: WebSocketListener,
    ): WebSocket {
        val url = serverUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegment("ws").build()
        return client.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $token").build(), listener)
    }

    private suspend inline fun <reified T> call(
        serverUrl: String,
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        acceptGone: Boolean = false,
    ): T =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(serverUrl.trimEnd('/') + path)
                    .method(method, body?.toRequestBody(jsonType))
                    .apply {
                        token?.let { header("Authorization", "Bearer $it") }
                        headers.forEach { (name, value) -> header(name, value) }
                    }.build()
            val response =
                try {
                    client.newCall(request).execute()
                } catch (e: java.io.IOException) {
                    throw SyncApiException(0, "Sync server unreachable: ${e.message}", e)
                }
            response.use {
                val text = it.body.string()
                if (!it.isSuccessful && !(acceptGone && it.code == 410)) {
                    throw SyncApiException(it.code, "Sync server answered ${it.code}: $text")
                }
                json.decodeFromString<T>(text)
            }
        }
}
