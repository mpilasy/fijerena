package org.njarasoa.fijerena.core.network.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** See the rock solid stability resilience plan → F-07. */
class SyncCodecTest {
    private val crypto = AccountKeyCrypto(AccountKeyCrypto.newAccountKey())
    private val record =
        SyncRecord(SyncKey("profile", "provider", SyncKind.FAVORITE_STREAM, "m1", "MOVIES"), hlc = 1_000, payload = """{"name":"Film"}""")

    @Test
    fun `a record survives the round trip`() {
        assertEquals(record, SyncCodec.decode(SyncCodec.encode(record, crypto), crypto))
    }

    /**
     * The worst case of the device diagnostics sync plan: a report at its 12,000-character cap whose
     * texts are all quotes and backslashes, which the setting and the envelope escape twice more.
     */
    @Test
    fun `a full-size device report sealed stays under the server's 64 Ki`() {
        val texts = "\"\\".repeat(DeviceReportBuilder.MESSAGE_MAX)
        val log = (1..40).map { "${1_800_000_000_000L - it * 60_000L} $texts · screen: $texts\njava.io.IOException: $texts" }
        val report =
            DeviceReportBuilder.build(
                DeviceReportBuilder.Input(1_800_000_000_000L, "0.9.20261111000000", 0, 0, 0, log, emptyList(), emptyList()),
            )
        val value = DeviceReportBuilder.encode(report)
        val setting =
            SyncRecord(
                SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, "device_report:0123456789abcdef0123456789abcdef"),
                hlc = 1_000,
                payload = SyncPayloads.encode(SyncPayloads.Setting(kotlinx.serialization.json.JsonPrimitive(value))),
            )

        val sealed = SyncCodec.encode(setting, crypto).payload

        assertTrue(
            value.length.toString(),
            value.length in (DeviceReportBuilder.MAX_ENCODED_CHARS - 1_000)..DeviceReportBuilder.MAX_ENCODED_CHARS,
        )
        assertTrue(sealed.length.toString(), sealed.length <= 64 * 1024)
        assertEquals(setting, SyncCodec.decode(SyncCodec.encode(setting, crypto), crypto))
    }

    @Test
    fun `a server flipping the deletion flag is caught`() {
        val wire = SyncCodec.encode(record, crypto)
        assertNull(SyncCodec.decode(wire.copy(deleted = true), crypto))
    }

    @Test
    fun `a server rewriting the clock value is caught`() {
        val wire = SyncCodec.encode(record, crypto)
        assertNull(SyncCodec.decode(wire.copy(updatedAt = Long.MAX_VALUE), crypto))
        assertNull(SyncCodec.decode(wire.copy(updatedAt = 1), crypto))
    }

    @Test
    fun `records sealed before the sealed copies existed are still read`() {
        val keyId = crypto.keyId(record.key)

        @Suppress("ktlint:standard:max-line-length")
        val legacyEnvelope = """{"profileKey":"profile","providerKey":"provider","kind":"favorite_stream","itemId":"m1","contentType":"MOVIES","payload":"{\"name\":\"Film\"}"}"""
        val wire = SyncWire.Record(key = keyId, updatedAt = 1_000, deleted = false, payload = crypto.seal(legacyEnvelope, aad = keyId))
        assertEquals(record, SyncCodec.decode(wire, crypto))
    }
}
