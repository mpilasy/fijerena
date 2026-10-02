package org.njarasoa.fijerena.core.network.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-07. */
class SyncCodecTest {
    private val crypto = AccountKeyCrypto(AccountKeyCrypto.newAccountKey())
    private val record = SyncRecord(SyncKey("profile", "provider", SyncKind.FAVORITE_STREAM, "m1", "MOVIES"), hlc = 1_000, payload = """{"name":"Film"}""")

    @Test
    fun `a record survives the round trip`() {
        assertEquals(record, SyncCodec.decode(SyncCodec.encode(record, crypto), crypto))
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
        val legacyEnvelope = """{"profileKey":"profile","providerKey":"provider","kind":"favorite_stream","itemId":"m1","contentType":"MOVIES","payload":"{\"name\":\"Film\"}"}"""
        val wire = SyncWire.Record(key = keyId, updatedAt = 1_000, deleted = false, payload = crypto.seal(legacyEnvelope, aad = keyId))
        assertEquals(record, SyncCodec.decode(wire, crypto))
    }
}
