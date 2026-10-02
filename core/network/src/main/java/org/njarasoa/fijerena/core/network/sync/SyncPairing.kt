package org.njarasoa.fijerena.core.network.sync

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

/**
 * What pairing QR codes carry, and the one-time key agreement of a handoff. See
 * `docs/plans/20260929_live-sync-plan.md` → Security (pairing).
 *
 * - [Invite]: shown by a device of an account; the device that scans it joins with the pairing code
 *   and the account key in it. The key is on screen: shown only on demand, never saved as an image.
 * - [HandoffRequest]: shown by a device that wants to join but can't scan (a TV); a device of the
 *   account scans it and sends the TV a pairing code and the account key through the server,
 *   sealed to the one-time public key in the QR code — so the server sees nothing readable, and
 *   can't substitute its own key, which never passes through it.
 */
sealed interface PairingQr {
    val serverUrl: String

    data class Invite(
        override val serverUrl: String,
        val pairingCode: String,
        val accountKey: ByteArray,
    ) : PairingQr

    data class HandoffRequest(
        override val serverUrl: String,
        val handoffId: String,
        val publicKey: ByteArray,
    ) : PairingQr

    companion object {
        private const val SCHEME = "fijerena-sync"
        private const val VERSION = "1"

        fun encode(qr: PairingQr): String =
            when (qr) {
                is Invite -> uri("join", "u" to qr.serverUrl, "c" to qr.pairingCode, "k" to B64URL.encodeToString(qr.accountKey))
                is HandoffRequest -> uri("handoff", "u" to qr.serverUrl, "h" to qr.handoffId, "p" to B64URL.encodeToString(qr.publicKey))
            }

        /** Null for anything that isn't a Fijerena pairing code of a version this app reads. */
        fun decode(text: String): PairingQr? =
            runCatching {
                val uri = URI(text.trim())
                if (uri.scheme != SCHEME) return null
                val params =
                    uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
                        val (k, v) = it.split('=', limit = 2)
                        k to URLDecoder.decode(v, "UTF-8")
                    }
                if (params["v"] != VERSION) return null
                val url = params["u"] ?: return null
                when (uri.host) {
                    "join" -> Invite(url, params["c"] ?: return null, B64URL.decode(params["k"] ?: return null))
                    "handoff" -> HandoffRequest(url, params["h"] ?: return null, B64URL.decode(params["p"] ?: return null))
                    else -> null
                }
            }.getOrNull()

        private fun uri(
            kind: String,
            vararg params: Pair<String, String>,
        ) = "$SCHEME://$kind?" + (listOf("v" to VERSION) + params).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }
}

/** P-256 ECDH for a handoff: the shared AES-256-GCM key both ends derive, bound to the handoff id. */
internal object HandoffKeys {
    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun publicKey(encoded: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    fun sharedKey(
        own: PrivateKey,
        other: PublicKey,
        handoffId: String,
    ): SecretKeySpec {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own)
        agreement.doPhase(other, true)
        return SecretKeySpec(Hkdf.derive(agreement.generateSecret(), "fijerena-sync handoff v1 $handoffId", 32), "AES")
    }
}
