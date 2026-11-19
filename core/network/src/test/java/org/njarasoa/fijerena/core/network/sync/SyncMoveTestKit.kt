package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import kotlinx.serialization.json.JsonPrimitive
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * What the move tests share: one account key for the group, a sync clock, devices with their own
 * link store and app settings, and records sealed with the group's key.
 */
class SyncMoveTestKit {
    val accountKey = AccountKeyCrypto.newAccountKey()
    val crypto = AccountKeyCrypto(accountKey)
    private var now = 1_800_000_000_000L
    val clock: suspend () -> Long = { ++now }

    /** Every batch that a device's applier was given. */
    val applied = mutableListOf<SyncRecord>()

    /** What [LocalRecords.pending] gives, for every device: the local changes not sent yet. */
    var pending = mutableListOf<LocalRecords.Outgoing>()
    var seeds = 0

    /** Database, applier and local records fakes for the tests that run [SyncEngine]. */
    fun mockEngineParts() {
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkConstructor(SyncApplier::class)
        coEvery { anyConstructed<SyncApplier>().apply(any()) } answers {
            val batch = firstArg<List<SyncRecord>>()
            applied += batch
            SyncApplier.Result(batch.size, 0, emptyList(), emptySet(), emptySet(), false)
        }
        mockkConstructor(LocalRecords::class)
        coEvery { anyConstructed<LocalRecords>().seedEverything() } answers { seeds += 1 }
        coEvery { anyConstructed<LocalRecords>().pending(any()) } answers { pending.filter { it.wanted }.take(firstArg()) }
        coEvery { anyConstructed<LocalRecords>().pendingCount() } answers { pending.count { it.wanted } }
        coEvery { anyConstructed<LocalRecords>().markAllSent(any()) } coAnswers {
            firstArg<List<LocalRecords.Outgoing>>().forEach { it.markSent() }
        }
    }

    private val sent = mutableSetOf<LocalRecords.Outgoing>()
    private val LocalRecords.Outgoing.wanted get() = this !in sent

    /** A local change of this device that waits to be sent. */
    fun pendingChange(record: SyncRecord) {
        lateinit var outgoing: LocalRecords.Outgoing
        outgoing = LocalRecords.Outgoing(record) { sent += outgoing }
        pending += outgoing
    }

    inner class Device(
        server: FakeSyncServer,
        name: String,
        credentials: SyncWire.DeviceCredentials = server.addDevice(name),
    ) {
        private val prefsFiles = mutableMapOf<String, SharedPreferences>()
        val context =
            mockk<Context>(relaxed = true).also { context ->
                every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
            }
        val store = SyncAccountStore(FakeSharedPreferences())
        val id = credentials.deviceId
        val settings get() = AppSettings(context)

        init {
            store.saveLink(server.url, credentials, accountKey)
            store.seeded = true
            store.startOver(SyncKind.KEPT)
        }

        fun mover(api: SyncApi) = SyncServerMove(context, api, store, clock)

        fun engine(api: SyncApi) = SyncEngine(context, api, store, mover(api))

        /** The build that [deviceId] reported, as this device holds it. */
        fun knowsBuild(
            deviceId: String,
            vararg features: String,
        ) = settings.applyRemoteSetting(
            AppSettings.deviceBuildSettingKey(deviceId),
            SyncKind.SHARED,
            JsonPrimitive(
                SyncPayloads.json.encodeToString(DeviceBuild.serializer(), DeviceBuild(app = "tv", features = features.toList())),
            ),
        )

        /** The group's name for [deviceId], as this device holds it. */
        fun knowsName(
            deviceId: String,
            name: String,
        ) = settings.applyRemoteSetting(AppSettings.deviceNameSettingKey(deviceId), SyncKind.SHARED, JsonPrimitive(name))
    }

    fun item(
        id: String,
        hlc: Long,
        deleted: Boolean = false,
    ) = SyncRecord(
        SyncKey("profile", "provider", SyncKind.FAVORITE_STREAM, id, "MOVIES"),
        hlc,
        deleted,
        if (deleted) null else """{"name":"$id"}""",
    )

    fun setting(
        key: String,
        value: String,
        hlc: Long,
    ) = SyncRecord(
        SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, key),
        hlc,
        payload = SyncPayloads.encode(SyncPayloads.Setting(JsonPrimitive(value))),
    )

    fun volatile(
        kind: String,
        deviceId: String,
        hlc: Long,
    ) = SyncRecord(SyncKey(SyncKind.SHARED, "", kind, deviceId), hlc, payload = "{}")

    fun seal(record: SyncRecord) = SyncCodec.encode(record, crypto)

    fun open(wire: SyncWire.Record) = SyncCodec.decode(wire, crypto)

    /** The records of [server] that the move records are not, opened. */
    fun data(server: FakeSyncServer) = server.records.mapNotNull(::open).filterNot { SyncServerMove.isMoveKey(it.key) }

    fun moveRecord(server: FakeSyncServer): SyncServerMove.MoveRecord? =
        server.records
            .mapNotNull(::open)
            .firstOrNull { it.key.itemId == SyncServerMove.KEY_MOVE }
            ?.let { SyncPayloads.json.decodeFromString(SyncServerMove.MoveRecord.serializer(), valueOf(it)) }

    fun previousRecord(server: FakeSyncServer): SyncRecord? =
        server.records.mapNotNull(::open).firstOrNull { it.key.itemId == SyncServerMove.KEY_PREVIOUS_SERVER }

    fun valueOf(record: SyncRecord): String = SyncPayloads.decode<SyncPayloads.Setting>(record.payload).value.content
}
