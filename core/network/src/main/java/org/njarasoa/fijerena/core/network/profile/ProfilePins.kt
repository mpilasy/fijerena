package org.njarasoa.fijerena.core.network.profile

import org.njarasoa.fijerena.core.network.AppSettings
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A profile's optional 4-digit PIN (the profile pin plan): set, removed and
 * checked here. Only a salted PBKDF2 hash is kept ([AppSettings.pinHash], synced per profile);
 * after [FREE_TRIES] wrong PINs in a row each further one waits, from [FIRST_WAIT_MS] doubling up
 * to [MAX_WAIT_MS], counted per device. It keeps kids and guests out; it is no real security.
 */
class ProfilePins(
    private val settings: AppSettings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** What [check] found. */
    sealed interface Check {
        data object Right : Check

        /** [triesLeft] before a wait starts (0 when the next wrong one waits too). */
        data class Wrong(
            val triesLeft: Int,
            /** When the next try is allowed, epoch ms; 0 when it is allowed now. */
            val waitUntil: Long,
        ) : Check

        /** Too many wrong PINs: nothing is checked before [until] (epoch ms). */
        data class Waiting(
            val until: Long,
        ) : Check
    }

    fun hasPin(profileId: String): Boolean = settings.pinHash(profileId) != null

    /** Sets [profileId]'s PIN; [pin] must be [isValid]. */
    fun set(
        profileId: String,
        pin: String,
    ) {
        require(isValid(pin)) { "A PIN is 4 digits" }
        settings.setPinHash(profileId, hash(pin, ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }))
        settings.setPinThrottle(profileId, failures = 0, waitUntil = 0L)
    }

    fun remove(profileId: String) {
        settings.setPinHash(profileId, null)
        settings.setPinThrottle(profileId, failures = 0, waitUntil = 0L)
    }

    /** Whether [pin] is [profileId]'s — always [Check.Right] for a profile without one. */
    fun check(
        profileId: String,
        pin: String,
    ): Check {
        val stored = settings.pinHash(profileId) ?: return Check.Right
        val waitUntil = settings.pinWaitUntil(profileId)
        if (now() < waitUntil) return Check.Waiting(waitUntil)
        if (matches(pin, stored)) {
            settings.setPinThrottle(profileId, failures = 0, waitUntil = 0L)
            return Check.Right
        }
        val failures = settings.pinFailures(profileId) + 1
        val next = if (failures >= FREE_TRIES) now() + waitAfter(failures) else 0L
        settings.setPinThrottle(profileId, failures, next)
        return Check.Wrong(triesLeft = (FREE_TRIES - failures).coerceAtLeast(0), waitUntil = next)
    }

    /**
     * Whether [pin] is the PIN of any of [profileIds] — the Expert profiles', where any admin's PIN
     * will do. Wrong tries count against the first one's (the oldest Expert's), so the wait can't
     * be dodged by spreading guesses. Right for an empty list.
     */
    fun checkAny(
        profileIds: List<String>,
        pin: String,
    ): Check {
        val first = profileIds.firstOrNull() ?: return Check.Right
        return checkAnyCounted(profileIds, pin, settings.pinFailures(first), settings.pinWaitUntil(first)) { failures, waitUntil ->
            settings.setPinThrottle(first, failures, waitUntil)
        }.check
    }

    /** What [checkToLeaveChild] found, and whose PIN it was when right. */
    data class LeaveCheck(
        val check: Check,
        val pinOf: String?,
    )

    /**
     * [checkAny] for the child lock: whether [pin] is the PIN of any of [profileIds] (the Expert
     * profiles with a PIN), to leave a child profile. Wrong tries count on the child lock's own
     * device count ([AppSettings.childLockFailures]), never on a profile's, so a child's guesses
     * don't make the Expert wait in Settings. [LeaveCheck.pinOf] says which profile's PIN it was.
     */
    fun checkToLeaveChild(
        profileIds: List<String>,
        pin: String,
    ): LeaveCheck =
        checkAnyCounted(profileIds, pin, settings.childLockFailures, settings.childLockWaitUntil) { failures, waitUntil ->
            settings.setChildLockThrottle(failures, waitUntil)
        }

    private fun checkAnyCounted(
        profileIds: List<String>,
        pin: String,
        failuresSoFar: Int,
        waitUntil: Long,
        setThrottle: (failures: Int, waitUntil: Long) -> Unit,
    ): LeaveCheck {
        if (profileIds.isEmpty()) return LeaveCheck(Check.Right, null)
        if (now() < waitUntil) return LeaveCheck(Check.Waiting(waitUntil), null)
        val matched = profileIds.firstOrNull { id -> settings.pinHash(id)?.let { matches(pin, it) } == true }
        if (matched != null) {
            setThrottle(0, 0L)
            return LeaveCheck(Check.Right, matched)
        }
        val failures = failuresSoFar + 1
        val next = if (failures >= FREE_TRIES) now() + waitAfter(failures) else 0L
        setThrottle(failures, next)
        return LeaveCheck(Check.Wrong(triesLeft = (FREE_TRIES - failures).coerceAtLeast(0), waitUntil = next), null)
    }

    /** When [profileId]'s PIN may be tried again, epoch ms, or null when it may now. */
    fun waitingUntil(profileId: String): Long? = settings.pinWaitUntil(profileId).takeIf { it > now() }

    companion object {
        const val LENGTH = 4
        const val FREE_TRIES = 5
        const val FIRST_WAIT_MS = 30_000L
        const val MAX_WAIT_MS = 15 * 60_000L
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 100_000
        private const val KEY_BITS = 256

        fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

        /** The wait after the [failures]th wrong PIN in a row: 30 s at the 5th, doubling, at most 15 min. */
        fun waitAfter(failures: Int): Long {
            val doublings = (failures - FREE_TRIES).coerceIn(0, MAX_DOUBLINGS)
            return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
        }

        // 30 s × 2^5 = 16 min, past the cap already.
        private const val MAX_DOUBLINGS = 5

        private fun hash(
            pin: String,
            salt: ByteArray,
        ): String {
            val encoder = Base64.getEncoder()
            return encoder.encodeToString(salt) + ":" + encoder.encodeToString(derive(pin, salt))
        }

        private fun matches(
            pin: String,
            stored: String,
        ): Boolean {
            val parts = stored.split(':')
            if (parts.size != 2) return false
            val decoder = Base64.getDecoder()
            val salt = runCatching { decoder.decode(parts[0]) }.getOrNull() ?: return false
            val expected = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return false
            return MessageDigest.isEqual(derive(pin, salt), expected)
        }

        private fun derive(
            pin: String,
            salt: ByteArray,
        ): ByteArray =
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS))
                .encoded
    }
}
