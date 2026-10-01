package org.njarasoa.fijerena.core.network.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What the server may see of a record: an opaque key, opaque provider/profile tags for its
 * cascades, and a sealed payload. See `docs/plans/20260929_live-sync-plan.md` → Security.
 */
interface SyncCrypto {
    /** The record's key as the server stores it; the same [SyncKey] always gives the same id. */
    fun keyId(key: SyncKey): String

    /** A provider or profile key as the server groups by it. */
    fun tag(value: String): String

    /** [aad] binds the ciphertext to its record: opened under any other key id, it fails. */
    fun seal(
        plaintext: String,
        aad: String,
    ): String

    /** Null when [sealed] can't be opened — another account key, another record, or tampering. */
    fun open(
        sealed: String,
        aad: String,
    ): String?
}

/**
 * End-to-end encryption with the account key every device of an account shares (Phase 8). Two
 * subkeys are derived from it (HKDF-SHA256): one HMACs record keys and tags, so the server can
 * upsert and cascade without learning item ids, providers or profiles; the other encrypts payloads
 * with AES-256-GCM, a fresh random nonce each time, the record's key id as associated data.
 */
class AccountKeyCrypto(
    accountKey: ByteArray,
) : SyncCrypto {
    init {
        require(accountKey.size == KEY_BYTES) { "Account key must be $KEY_BYTES bytes" }
    }

    private val macKey = SecretKeySpec(Hkdf.derive(accountKey, "fijerena-sync mac v1", KEY_BYTES), "HmacSHA256")
    private val encKey = SecretKeySpec(Hkdf.derive(accountKey, "fijerena-sync enc v1", KEY_BYTES), "AES")

    override fun keyId(key: SyncKey): String =
        hmac("key", listOf(key.profileKey, key.providerKey, key.kind, key.itemId, key.contentType).joinToString(SEPARATOR))

    override fun tag(value: String): String = hmac("tag", value)

    override fun seal(
        plaintext: String,
        aad: String,
    ): String = Aead.seal(encKey, plaintext.toByteArray(), aad.toByteArray())

    override fun open(
        sealed: String,
        aad: String,
    ): String? = Aead.open(encKey, sealed, aad.toByteArray())?.let { String(it) }

    private fun hmac(
        purpose: String,
        value: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        return B64URL.encodeToString(mac.doFinal("$purpose$SEPARATOR$value".toByteArray()))
    }

    companion object {
        const val KEY_BYTES = 32

        // Not a character any of the fields can contain.
        private const val SEPARATOR = "\u0000"

        fun newAccountKey(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
    }
}

/** AES-256-GCM: `base64(nonce ‖ ciphertext ‖ tag)`. */
internal object Aead {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(
        key: SecretKeySpec,
        plaintext: ByteArray,
        aad: ByteArray,
    ): String {
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return B64.encodeToString(nonce + cipher.doFinal(plaintext))
    }

    fun open(
        key: SecretKeySpec,
        sealed: String,
        aad: ByteArray,
    ): ByteArray? {
        val bytes =
            try {
                B64.decode(sealed)
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (bytes.size <= NONCE_BYTES) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
            cipher.updateAAD(aad)
            cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        } catch (_: AEADBadTagException) {
            null
        }
    }
}

/** HKDF-SHA256 (RFC 5869) with an all-zero salt: input keys here are already uniformly random. */
internal object Hkdf {
    fun derive(
        ikm: ByteArray,
        info: String,
        length: Int,
    ): ByteArray {
        val prk = hmac(ByteArray(32), ikm)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info.toByteArray() + counter.toByte())
            val n = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
            counter++
        }
        return out
    }

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

/** Standard Base64 (payloads). */
internal object B64 {
    fun encodeToString(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}

/** URL-safe Base64 without padding (ids, tags, QR codes). */
internal object B64URL {
    fun encodeToString(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
}
