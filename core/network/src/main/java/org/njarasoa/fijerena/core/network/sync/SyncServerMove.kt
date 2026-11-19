package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

/**
 * Moves the sync group to another server, and watches the old server after a move (the sync server
 * move plan → Design).
 *
 * The data goes through the device: it pulls the records from the old server and pushes them to
 * the new server as they are, still sealed. It opens a record only to read its kind, and never
 * seals it again. So the move works between any two servers.
 *
 * Two shared setting records carry the move (SM-Q1). The sync engine takes them before the
 * applier, and this class keeps them in [SyncAccountStore], never in the app settings:
 * - `sync_move` ([KEY_MOVE]), on the old server: [MoveRecord].
 * - `sync_previous_server` ([KEY_PREVIOUS_SERVER]), on the new server: [PreviousServer].
 *
 * An older build skips both as setting keys that it does not know.
 */
class SyncServerMove(
    private val context: Context,
    private val api: SyncApi = SyncApi(),
    private val store: SyncAccountStore = SyncAccountStore(context),
    /** The sync clock: each record that this class writes gets the next value. */
    private val clock: suspend () -> Long = { SettingsDatabase.getInstance(context).settingsSyncDao().nextClock() },
) {
    /** The value of the `sync_move` record: the new address, and the move code of each old device id. */
    @Serializable
    data class MoveRecord(
        val serverUrl: String,
        val codes: Map<String, String>,
    )

    /**
     * The value of the `sync_previous_server` record: the old address, and the old device id and
     * token of the device that started the move. Every admin device uses the token to watch and
     * purge the old server.
     */
    @Serializable
    data class PreviousServer(
        val serverUrl: String,
        val deviceId: String,
        val deviceToken: String,
    )

    /** The last `sync_previous_server` record that this device took; [server] is null after a deletion. */
    @Serializable
    data class PreviousServerRecord(
        val hlc: Long,
        val server: PreviousServer? = null,
    )

    /** The state of a move that this device started, so that the move can continue after a stop. */
    @Serializable
    data class Progress(
        val serverUrl: String,
        val credentials: SyncWire.DeviceCredentials,
        /** The last step done (see [STEP_ACCOUNT] and the other step values). */
        val step: Int,
        /** The move code of each old device id. */
        val codes: Map<String, String> = emptyMap(),
        /** The `seq` of the last old-server record copied. */
        val copyCursor: Long = 0,
    )

    private val settings by lazy { AppSettings(context) }
    private val json = Json { ignoreUnknownKeys = true }

    /** A move that this device started and did not finish; [run] continues it. */
    val unfinished: Progress? get() = store.moveProgress

    /**
     * Step 0: why Move to Another Server is disabled; null when it is not. Asks the server for the
     * devices of the group. A move that this device started and did not finish is never blocked.
     * Throws [SyncApiException] when the server does not answer.
     */
    suspend fun moveBlock(): MoveBlock? {
        val link = store.link ?: return null
        if (store.moveProgress != null) return null
        if (store.previousServer != null) return MoveBlock.OldServerLeft
        val builds = settings.deviceBuilds()
        val behind =
            api
                .devices(link.serverUrl, link.deviceToken)
                .filter { !it.revoked && !it.current && it.id != link.deviceId }
                .filter { builds[it.id]?.canMoveServer != true }
        return if (behind.isEmpty()) null else MoveBlock.UpdateFirst(behind.map { groupName(it) })
    }

    /**
     * Step 1: [serverUrl] must be a compatible sync server with the `move` feature, and not the
     * server that the group uses now. Otherwise this throws [SyncApiException] with the status
     * [SyncApiException.INCOMPATIBLE]. [SyncWire.Info.setupSecretRequired] tells if a new account
     * there needs the setup secret.
     */
    suspend fun checkTarget(serverUrl: String): SyncWire.Info {
        val url = normalise(serverUrl)
        if (url == store.link?.serverUrl) {
            throw SyncApiException(SyncApiException.INCOMPATIBLE, "The sync group already uses $url")
        }
        val info = api.info(url)
        if (info.service != SERVICE || info.protocol != SyncWire.PROTOCOL) {
            throw SyncApiException(
                SyncApiException.INCOMPATIBLE,
                "Not a compatible Fijerena sync server (${info.service}, protocol ${info.protocol})",
            )
        }
        if (SyncWire.Info.FEATURE_MOVE !in info.features) {
            throw SyncApiException(SyncApiException.INCOMPATIBLE, "The sync server $url can't take a moved group: update it first")
        }
        return info
    }

    /**
     * Steps 1 to 8: moves the group to [serverUrl] and switches this device to it. When a move
     * started here did not finish, this continues that move and ignores [serverUrl] and
     * [setupSecret]: it never makes a second account. [onState] gets each stage.
     *
     * The caller makes sure that no sync pass runs until this returns. Throws [SyncApiException]
     * when a server call fails, and [IllegalStateException] when this device is not linked, step 0
     * blocks the move, or a server refuses a move record. After a failure, call this again to
     * continue.
     */
    suspend fun run(
        serverUrl: String,
        setupSecret: String?,
        onState: (MoveState) -> Unit,
    ) {
        val link = checkNotNull(store.link) { "This device is not linked" }
        val crypto = AccountKeyCrypto(link.accountKey)
        var progress = store.moveProgress ?: start(link, serverUrl, setupSecret, onState)
        val newUrl = progress.serverUrl
        val newToken = progress.credentials.deviceToken

        fun save(next: Progress) {
            progress = next
            store.moveProgress = next
        }

        // Step 3: one move code per other device that is not revoked.
        if (progress.step < STEP_CODES) {
            onState(MoveState.Running(MoveState.Stage.CREATING))
            val others = api.devices(link.serverUrl, link.deviceToken).filter { !it.revoked && !it.current && it.id != link.deviceId }
            for (device in others.filter { it.id !in progress.codes }) {
                val code = api.createMoveCode(newUrl, newToken).code
                save(progress.copy(codes = progress.codes + (device.id to code)))
            }
            save(progress.copy(step = STEP_CODES))
        }
        // Steps 4 and 6: the snapshot, then what came in until the move record was written.
        val copyStep: suspend (Int) -> Unit = { step ->
            onState(MoveState.Running(MoveState.Stage.COPYING))
            val head =
                copy(link, newUrl, newToken, progress.copyCursor, crypto) { cursor, received, total ->
                    save(progress.copy(copyCursor = cursor))
                    onState(MoveState.Running(MoveState.Stage.COPYING, received, total))
                }
            save(progress.copy(step = step, copyCursor = head))
        }
        if (progress.step < STEP_SNAPSHOT) copyStep(STEP_SNAPSHOT)
        // Step 5: the move record on the old server.
        if (progress.step < STEP_MOVE_RECORD) {
            val move = MoveRecord(newUrl, progress.codes)
            pushOne(link.serverUrl, link.deviceToken, crypto, setting(KEY_MOVE, clock(), encode(MoveRecord.serializer(), move)))
            save(progress.copy(step = STEP_MOVE_RECORD))
        }
        if (progress.step < STEP_LATE_COPY) copyStep(STEP_LATE_COPY)
        // Step 7: the old-server record on the new server.
        onState(MoveState.Running(MoveState.Stage.SWITCHING))
        if (progress.step < STEP_PREVIOUS) {
            val old = PreviousServer(link.serverUrl, link.deviceId, link.deviceToken)
            val hlc = clock()
            pushOne(newUrl, newToken, crypto, setting(KEY_PREVIOUS_SERVER, hlc, encode(PreviousServer.serializer(), old)))
            store.previousServerRecord = PreviousServerRecord(hlc, old)
            save(progress.copy(step = STEP_PREVIOUS))
        }
        // Step 8.
        store.switchServer(newUrl, progress.credentials)
        Log.i(TAG, "Moved the sync group to $newUrl")
        onState(MoveState.Done)
    }

    /** Steps 0 to 2 of a new move: the checks, then the account on the new server. */
    private suspend fun start(
        link: SyncAccountStore.Link,
        serverUrl: String,
        setupSecret: String?,
        onState: (MoveState) -> Unit,
    ): Progress {
        onState(MoveState.Running(MoveState.Stage.CHECKING))
        moveBlock()?.let { throw IllegalStateException("The move is blocked: $it") }
        val url = normalise(serverUrl)
        checkTarget(url)
        onState(MoveState.Running(MoveState.Stage.CREATING))
        val credentials = api.createAccount(url, deviceName(link), setupSecret)
        return Progress(url, credentials, STEP_ACCOUNT).also { store.moveProgress = it }
    }

    /**
     * Asks the old server how far the move is (an open Live Sync screen only: no device watches
     * the old server in the background). Throws [IllegalStateException] when the group has no old
     * server.
     */
    suspend fun oldServerState(): OldServerState {
        val old = checkNotNull(store.previousServer) { "The sync group has no old server" }
        val info =
            try {
                api.info(old.serverUrl)
            } catch (e: SyncApiException) {
                return OldServerState.Unreachable(e.message.orEmpty())
            }
        if (SyncWire.Info.FEATURE_PURGE !in info.features) return OldServerState.NoPurge
        val devices =
            try {
                api.devices(old.serverUrl, old.deviceToken)
            } catch (e: SyncApiException) {
                return OldServerState.Unreachable(e.message.orEmpty())
            }
        // The device whose token the record holds started the move: it is on the new server.
        val notMoved = devices.filter { !it.revoked && it.id != old.deviceId }
        return if (notMoved.isEmpty()) OldServerState.Ready else OldServerState.NotMoved(notMoved.map { groupName(it) })
    }

    /**
     * Purge Old Server: deletes the group's account on the old server, then deletes the old-server
     * record on the new server. Throws [SyncApiException] (409 while another old device is not
     * revoked) and [IllegalStateException] when the group has no old server.
     */
    suspend fun purgeOldServer() {
        val old = checkNotNull(store.previousServer) { "The sync group has no old server" }
        api.deleteAccount(old.serverUrl, old.deviceToken)
        Log.i(TAG, "Purged the sync group from the old server ${old.serverUrl}")
        forgetOldServer()
    }

    /** Forget Old Server: deletes the old-server record on the new server, and purges nothing (SM-Q5). */
    suspend fun forgetOldServer() {
        val held = store.previousServerRecord?.takeIf { it.server != null } ?: return
        val link = checkNotNull(store.link) { "This device is not linked" }
        val hlc = maxOf(clock(), held.hlc + 1)
        val tombstone = SyncRecord(settingKey(KEY_PREVIOUS_SERVER), hlc, deleted = true)
        pushOne(link.serverUrl, link.deviceToken, AccountKeyCrypto(link.accountKey), tombstone)
        store.previousServerRecord = PreviousServerRecord(hlc)
    }

    /**
     * A move record that a pull brought ([isMoveKey]): the sync engine gives it here, never to the
     * applier. A newer record replaces the one held; a deletion drops it.
     */
    internal fun take(record: SyncRecord) {
        when (record.key.itemId) {
            KEY_PREVIOUS_SERVER -> {
                val held = store.previousServerRecord
                if (held != null && held.hlc >= record.hlc) return
                val server = if (record.deleted) null else (decode(record, PreviousServer.serializer()) ?: return)
                store.previousServerRecord = PreviousServerRecord(record.hlc, server)
            }
        }
    }

    /**
     * Copies the records after [since] from the server of [from] to [toUrl], page by page, still
     * sealed and with the same clock values; skips what [copies] leaves out. Returns the `seq` of
     * the last record read. [onPage] gets the cursor and the counts after each page.
     */
    private suspend fun copy(
        from: SyncAccountStore.Link,
        toUrl: String,
        toToken: String,
        since: Long,
        crypto: SyncCrypto,
        onPage: suspend (cursor: Long, received: Int, total: Int?) -> Unit,
    ): Long {
        var cursor = since
        var received = 0
        while (true) {
            val page = api.pull(from.serverUrl, from.deviceToken, cursor, PAGE)
            if (page.resync && cursor != 0L) {
                // Tombstones after the cursor are gone: copy again from the start. The new server
                // refuses the records it has as stale.
                cursor = 0
                continue
            }
            val kept =
                page.records
                    .filter { wire -> SyncCodec.decode(wire, crypto)?.let(::copies) == true }
                    .map { it.copy(seq = null, cascade = null) }
            if (kept.isNotEmpty()) {
                api
                    .push(toUrl, toToken, kept)
                    .rejected
                    .filter { it.reason != "stale" }
                    .forEach { Log.w(TAG, "The new server refused a copied record: ${it.reason}") }
            }
            cursor = page.records.lastOrNull()?.seq ?: cursor
            received += page.records.size
            onPage(cursor, received, page.remaining?.let { (received + it).toInt() })
            if (!page.more) return cursor
        }
    }

    private suspend fun pushOne(
        serverUrl: String,
        token: String,
        crypto: SyncCrypto,
        record: SyncRecord,
    ) {
        val response = api.push(serverUrl, token, listOf(SyncCodec.encode(record, crypto)))
        if (response.accepted != 1) {
            val reason = response.rejected.firstOrNull()?.reason ?: "not accepted"
            throw IllegalStateException("The sync server refused the ${record.key.itemId} record: $reason")
        }
    }

    /** The name of this device for the new server: the group's name for it, else the old server's. */
    private suspend fun deviceName(link: SyncAccountStore.Link): String {
        settings.deviceName(link.deviceId)?.let { return it }
        val serverName =
            try {
                api.devices(link.serverUrl, link.deviceToken).firstOrNull { it.current }?.name
            } catch (e: SyncApiException) {
                Log.w(TAG, "Device name not read from the old server: ${e.message}")
                null
            }
        return serverName?.takeIf { it.isNotBlank() } ?: Build.MODEL?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
    }

    /** The group's name for [device], else the server's. */
    private fun groupName(device: SyncWire.Device): String = settings.deviceName(device.id) ?: device.name

    private fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = json.encodeToString(serializer, value)

    private fun <T> decode(
        record: SyncRecord,
        serializer: KSerializer<T>,
    ): T? =
        try {
            json.decodeFromString(serializer, SyncPayloads.decode<SyncPayloads.Setting>(record.payload).value.content)
        } catch (e: IllegalArgumentException) {
            // cancellation-ok: non-suspend
            Log.w(TAG, "Dropped a ${record.key.itemId} record that doesn't read: ${e.message}")
            null
        }

    companion object {
        private const val TAG = "SyncServerMove"
        private const val SERVICE = "fijerena-sync"
        private const val DEFAULT_NAME = "Fijerena"
        private const val PAGE = 500

        /** The move record's setting key, on the old server. */
        const val KEY_MOVE = "sync_move"

        /** The old-server record's setting key, on the new server. */
        const val KEY_PREVIOUS_SERVER = "sync_previous_server"

        // The steps of [Progress.step]: the last step done.
        internal const val STEP_ACCOUNT = 2
        internal const val STEP_CODES = 3
        internal const val STEP_SNAPSHOT = 4
        internal const val STEP_MOVE_RECORD = 5
        internal const val STEP_LATE_COPY = 6
        internal const val STEP_PREVIOUS = 7

        /**
         * Setting keys that hold a server device id: the device's group name, its build and its
         * report. They point at dead ids on the new server, so the copy skips them (SM-Q3). Each
         * device reports its build and report again on the new server, and the new server knows
         * each device by the name that it gave when it joined.
         */
        private val DEVICE_KEYED_PREFIXES =
            listOf(
                AppSettings.DEVICE_NAME_SETTING_PREFIX,
                AppSettings.DEVICE_BUILD_SETTING_PREFIX,
                AppSettings.DEVICE_REPORT_SETTING_PREFIX,
            )

        /** Whether [key] is one of the two move records, which the sync engine gives to [take]. */
        fun isMoveKey(key: SyncKey): Boolean =
            key.kind == SyncKind.SETTING && key.profileKey == SyncKind.SHARED &&
                (key.itemId == KEY_MOVE || key.itemId == KEY_PREVIOUS_SERVER)

        /**
         * Whether the copy to the new server takes [record] (SM-Q3): not the volatile kinds, not
         * the records keyed by an old server device id, and not the move records. Deletions are
         * copied like any other record.
         */
        fun copies(record: SyncRecord): Boolean {
            val key = record.key
            if (key.kind in SyncKind.VOLATILE) return false
            if (key.kind != SyncKind.SETTING) return true
            return !isMoveKey(key) && DEVICE_KEYED_PREFIXES.none { key.itemId.startsWith(it) }
        }

        private fun normalise(serverUrl: String) = serverUrl.trim().trimEnd('/')

        private fun settingKey(key: String) = SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, key)

        private fun setting(
            key: String,
            hlc: Long,
            value: String,
        ) = SyncRecord(settingKey(key), hlc, payload = SyncPayloads.encode(SyncPayloads.Setting(JsonPrimitive(value))))
    }
}
