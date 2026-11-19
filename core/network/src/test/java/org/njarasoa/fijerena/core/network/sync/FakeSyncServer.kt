package org.njarasoa.fijerena.core.network.sync

import io.mockk.coEvery
import io.mockk.mockk

/**
 * One account on an in-memory sync server, with the rules of `server/src/account.ts` that the move
 * depends on: one row per key (the newest write wins, an equal or older one is `stale`), a new
 * `seq` for each write, pages of `limit` records, move codes that work one time, and
 * `DELETE /account` refused while another device is not revoked.
 */
class FakeSyncServer(
    val url: String,
    val features: List<String> = listOf(SyncWire.Info.FEATURE_MOVE, SyncWire.Info.FEATURE_PURGE),
    private val setupSecret: String? = null,
) {
    data class Device(
        val id: String,
        val name: String,
        val token: String,
        var revoked: Boolean = false,
    )

    val devices = mutableListOf<Device>()

    /** False: every call fails as if the server were down. */
    var reachable = true
    private val rows = LinkedHashMap<String, SyncWire.Record>()
    private val codes = mutableMapOf<String, Boolean>()
    var head = 0L
        private set
    var accountsCreated = 0
        private set
    var deleted = false
        private set

    /** Every record as the server holds it, in `seq` order. */
    val records: List<SyncWire.Record> get() = rows.values.sortedBy { it.seq }

    fun info() = SyncWire.Info("fijerena-sync", SyncWire.PROTOCOL, setupSecretRequired = setupSecret != null, features = features)

    fun createAccount(
        name: String,
        secret: String?,
    ): SyncWire.DeviceCredentials {
        if (setupSecret != null && secret != setupSecret) throw SyncApiException(403, "setup secret refused")
        accountsCreated++
        return addDevice(name)
    }

    /** A device of the account, as if it had joined before the test. */
    fun addDevice(name: String): SyncWire.DeviceCredentials {
        val id = "$url#d${devices.size + 1}"
        val device = Device(id, name, "token-$id")
        devices += device
        return SyncWire.DeviceCredentials("acc@$url", id, device.token)
    }

    fun createPairing(
        token: String,
        move: Boolean,
    ): SyncWire.Pairing {
        auth(token)
        val code = "acc.${codes.size + 1}"
        codes[code] = true
        return SyncWire.Pairing(code, if (move) null else Long.MAX_VALUE)
    }

    fun pair(
        code: String,
        name: String,
    ): SyncWire.DeviceCredentials {
        if (codes.remove(code) != true) throw SyncApiException(403, "pairing code refused")
        return addDevice(name)
    }

    fun push(
        token: String,
        records: List<SyncWire.Record>,
    ): SyncWire.PushResponse {
        auth(token)
        val rejected = mutableListOf<SyncWire.Rejection>()
        var accepted = 0
        records.forEach { record ->
            val held = rows[record.key]
            if (held != null && held.updatedAt >= record.updatedAt) {
                rejected += SyncWire.Rejection(record.key, "stale")
            } else {
                rows.remove(record.key)
                rows[record.key] = record.copy(seq = ++head, cascade = null)
                accepted++
            }
        }
        return SyncWire.PushResponse(head, accepted, rejected)
    }

    fun pull(
        token: String,
        since: Long,
        limit: Int,
    ): SyncWire.PullResponse {
        auth(token)
        val after = records.filter { it.seq!! > since }
        val page = after.take(limit)
        return SyncWire.PullResponse(head, more = after.size > limit, records = page, remaining = (after.size - page.size).toLong())
    }

    fun deviceList(token: String): List<SyncWire.Device> {
        val caller = auth(token)
        return devices.map { SyncWire.Device(it.id, it.name, 0, 0, it.revoked, current = it === caller) }
    }

    fun revoke(
        token: String,
        id: String,
    ) {
        auth(token)
        devices.first { it.id == id }.revoked = true
    }

    fun deleteAccount(token: String): SyncWire.AccountDeleted {
        val caller = auth(token)
        if (devices.any { it !== caller && !it.revoked }) throw SyncApiException(409, "other devices are not revoked")
        deleted = true
        devices.clear()
        rows.clear()
        codes.clear()
        return SyncWire.AccountDeleted()
    }

    /** Puts [record] on the server as it is, as if a device had pushed it. */
    fun put(record: SyncWire.Record) {
        rows.remove(record.key)
        rows[record.key] = record.copy(seq = ++head)
    }

    private fun auth(token: String): Device =
        devices.firstOrNull { it.token == token && !it.revoked } ?: throw SyncApiException(401, "token refused")

    companion object {
        /**
         * A [SyncApi] that sends each call to the server of its address. [before] runs before each
         * call with its name and address, so a test can make a call fail.
         */
        fun api(
            vararg servers: FakeSyncServer,
            before: (call: String, url: String) -> Unit = { _, _ -> },
        ): SyncApi {
            fun at(url: String): FakeSyncServer =
                servers.firstOrNull { it.url == url.trimEnd('/') && it.reachable }
                    ?: throw SyncApiException(0, "Sync server $url unreachable")
            return mockk {
                coEvery { info(any()) } answers {
                    before("info", firstArg())
                    at(firstArg()).info()
                }
                coEvery { createAccount(any(), any(), any()) } answers {
                    before("createAccount", firstArg())
                    at(firstArg()).createAccount(secondArg(), thirdArg())
                }
                coEvery { createPairing(any(), any()) } answers {
                    before("createPairing", firstArg())
                    at(firstArg()).createPairing(secondArg(), move = false)
                }
                coEvery { createMoveCode(any(), any()) } answers {
                    before("createMoveCode", firstArg())
                    at(firstArg()).createPairing(secondArg(), move = true)
                }
                coEvery { pair(any(), any(), any()) } answers {
                    before("pair", firstArg())
                    at(firstArg()).pair(secondArg(), thirdArg())
                }
                coEvery { push(any(), any(), any()) } answers {
                    before("push", firstArg())
                    at(firstArg()).push(secondArg(), thirdArg())
                }
                coEvery { pull(any(), any(), any(), any()) } answers {
                    before("pull", firstArg())
                    at(firstArg()).pull(secondArg(), thirdArg(), arg(3))
                }
                coEvery { devices(any(), any()) } answers {
                    before("devices", firstArg())
                    at(firstArg()).deviceList(secondArg())
                }
                coEvery { revoke(any(), any(), any()) } answers {
                    before("revoke", firstArg())
                    at(firstArg()).revoke(secondArg(), thirdArg())
                }
                coEvery { deleteAccount(any(), any()) } answers {
                    before("deleteAccount", firstArg())
                    at(firstArg()).deleteAccount(secondArg())
                }
            }
        }
    }
}
