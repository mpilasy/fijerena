package org.njarasoa.fijerena.core.network.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/** The end-to-end encryption and pairing pieces — see docs/plans/20260929_live-sync-plan.md → Security. */
class SyncCryptoTest {
    private val key = SyncKey("profile", "provider", SyncKind.FAVORITE_STREAM, "m1", "MOVIES")

    @Test
    fun `HKDF matches RFC 5869 test case 3`() {
        val okm = Hkdf.derive(ByteArray(22) { 0x0b }, "", 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `key ids and tags are stable per account and reveal nothing`() {
        val accountKey = AccountKeyCrypto.newAccountKey()
        val crypto = AccountKeyCrypto(accountKey)
        assertEquals(crypto.keyId(key), AccountKeyCrypto(accountKey.copyOf()).keyId(key))
        assertNotEquals(crypto.keyId(key), AccountKeyCrypto(AccountKeyCrypto.newAccountKey()).keyId(key))
        assertNotEquals(crypto.keyId(key), crypto.keyId(key.copy(itemId = "m2")))
        assertFalse(crypto.keyId(key).contains("m1"))
        assertNotEquals(crypto.tag("provider"), crypto.keyId(key))
        assertEquals(43, crypto.tag("provider").length) // 32 bytes, URL-safe Base64
    }

    @Test
    fun `payloads open only with the same account key and record`() {
        val accountKey = AccountKeyCrypto.newAccountKey()
        val crypto = AccountKeyCrypto(accountKey)
        val sealed = crypto.seal("""{"name":"Film"}""", aad = "record-1")

        assertEquals("""{"name":"Film"}""", AccountKeyCrypto(accountKey).open(sealed, aad = "record-1"))
        assertFalse(sealed.contains("Film"))
        assertNotEquals(sealed, crypto.seal("""{"name":"Film"}""", aad = "record-1")) // fresh nonce
        // Another account, another record (a swapped payload), or tampering: unreadable.
        assertNull(AccountKeyCrypto(AccountKeyCrypto.newAccountKey()).open(sealed, aad = "record-1"))
        assertNull(crypto.open(sealed, aad = "record-2"))
        val bytes = B64.decode(sealed).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(crypto.open(B64.encodeToString(bytes), aad = "record-1"))
        assertNull(crypto.open("not base64!", aad = "record-1"))
    }

    @Test
    fun `pairing QR codes round-trip and reject anything else`() {
        val invite = PairingQr.Invite("https://sync.example.org", "abc.def", AccountKeyCrypto.newAccountKey())
        val decoded = PairingQr.decode(PairingQr.encode(invite)) as PairingQr.Invite
        assertEquals(invite.serverUrl, decoded.serverUrl)
        assertEquals(invite.pairingCode, decoded.pairingCode)
        assertTrue(invite.accountKey.contentEquals(decoded.accountKey))

        val handoff = PairingQr.HandoffRequest("http://10.0.2.2:8787", "0123456789abcdef0123456789abcdef", HandoffKeys.newKeyPair().public.encoded)
        val decodedHandoff = PairingQr.decode(PairingQr.encode(handoff)) as PairingQr.HandoffRequest
        assertEquals(handoff.handoffId, decodedHandoff.handoffId)
        assertTrue(handoff.publicKey.contentEquals(decodedHandoff.publicKey))

        assertNull(PairingQr.decode("https://example.org"))
        assertNull(PairingQr.decode("fijerena-sync://join?v=2&u=x&c=y&k=AAAA"))
        assertNull(PairingQr.decode("fijerena-sync://join?v=1&u=x"))
        assertNull(PairingQr.decode("garbage"))
    }

    @Test
    fun `both ends of a handoff derive the same key, bound to the handoff`() {
        val tv = HandoffKeys.newKeyPair()
        val phone = HandoffKeys.newKeyPair()
        val tvSide = HandoffKeys.sharedKey(tv.private, HandoffKeys.publicKey(phone.public.encoded), "h1")
        val phoneSide = HandoffKeys.sharedKey(phone.private, HandoffKeys.publicKey(tv.public.encoded), "h1")
        assertTrue(tvSide.encoded.contentEquals(phoneSide.encoded))

        val sealed = Aead.seal(phoneSide, "secret".toByteArray(), "h1".toByteArray())
        assertEquals("secret", String(Aead.open(tvSide, sealed, "h1".toByteArray())!!))
        // A third party with its own key pair, or another handoff, can't open it.
        val eve = HandoffKeys.newKeyPair()
        val eveKey = HandoffKeys.sharedKey(eve.private, HandoffKeys.publicKey(phone.public.encoded), "h1")
        assertNull(Aead.open(eveKey, sealed, "h1".toByteArray()))
        assertNull(Aead.open(SecretKeySpec(tvSide.encoded, "AES"), sealed, "h2".toByteArray()))
    }
}
