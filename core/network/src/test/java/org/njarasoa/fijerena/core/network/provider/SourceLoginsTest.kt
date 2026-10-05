package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ProviderRepository.Login
import org.njarasoa.fijerena.core.network.sync.SyncPayloads

/** docs/plans/20261005_shared-logins-plan.md → Phase 1. */
class SourceLoginsTest {
    private val main = Login("main", "pw-main")
    private val two = Login("two", "pw-two")
    private val three = Login("three", "pw-three")
    private val json = Json { ignoreUnknownKeys = true }

    private fun done(change: SourceLogins.Change) = (change as SourceLogins.Change.Done).logins

    @Test
    fun `add appends an extra login, trimmed`() {
        val logins = done(SourceLogins(main, listOf(two)).add("  three ", "pw-three"))
        assertEquals(SourceLogins(main, listOf(two, three)), logins)
    }

    @Test
    fun `add refuses a username already on the source, main or extra`() {
        val logins = SourceLogins(main, listOf(two))
        assertEquals(SourceLogins.Change.DuplicateUsername, logins.add("main", "x"))
        assertEquals(SourceLogins.Change.DuplicateUsername, logins.add("two", "x"))
    }

    @Test
    fun `add refuses a blank username`() {
        assertEquals(SourceLogins.Change.UnknownUsername, SourceLogins(main, emptyList()).add("  ", "x"))
    }

    @Test
    fun `passwords may repeat`() {
        val logins = done(SourceLogins(main, emptyList()).add("two", "pw-main"))
        assertEquals(listOf(Login("two", "pw-main")), logins.extras)
    }

    @Test
    fun `removing an extra login keeps the main one`() {
        assertEquals(SourceLogins(main, listOf(three)), done(SourceLogins(main, listOf(two, three)).remove("two")))
    }

    @Test
    fun `removing the main login promotes the first extra one`() {
        assertEquals(SourceLogins(two, listOf(three)), done(SourceLogins(main, listOf(two, three)).remove("main")))
    }

    @Test
    fun `removing the main login skips an extra login without a password`() {
        val noPassword = Login("imported", "")
        assertEquals(SourceLogins(two, listOf(noPassword)), done(SourceLogins(main, listOf(noPassword, two)).remove("main")))
    }

    @Test
    fun `the main login can't be removed when no other login has a password`() {
        assertEquals(SourceLogins.Change.PasswordNeeded, SourceLogins(main, listOf(Login("imported", ""))).remove("main"))
    }

    @Test
    fun `a login without a password can't become the main one`() {
        assertEquals(SourceLogins.Change.PasswordNeeded, SourceLogins(main, listOf(Login("imported", ""))).makeMain("imported"))
    }

    @Test
    fun `the last login can't be removed`() {
        assertEquals(SourceLogins.Change.LastLogin, SourceLogins(main, emptyList()).remove("main"))
    }

    @Test
    fun `removing an unknown username changes nothing`() {
        assertEquals(SourceLogins.Change.UnknownUsername, SourceLogins(main, listOf(two)).remove("nobody"))
    }

    @Test
    fun `make main swaps the two logins and keeps the others in place`() {
        assertEquals(SourceLogins(three, listOf(two, main)), done(SourceLogins(main, listOf(two, three)).makeMain("three")))
    }

    @Test
    fun `make main on the main login changes nothing`() {
        val logins = SourceLogins(main, listOf(two))
        assertEquals(logins, done(logins.makeMain("main")))
    }

    @Test
    fun `make main on an unknown username is refused`() {
        assertEquals(SourceLogins.Change.UnknownUsername, SourceLogins(main, listOf(two)).makeMain("nobody"))
    }

    /** [ProviderSettings] as a version before extra logins declared it (a subset is enough). */
    @Serializable
    private data class OlderProviderSettings(
        val watchHistorySize: Int = 25,
    )

    @Test
    fun `settings without the field decode to no extra logins`() {
        assertEquals(emptyList<String>(), json.decodeFromString<ProviderSettings>("""{"watchHistorySize":30}""").extraLogins)
    }

    @Test
    fun `settings round trip the extra logins, and an older version ignores them`() {
        val encoded = json.encodeToString(ProviderSettings(extraLogins = listOf("two", "three")))
        assertEquals(listOf("two", "three"), json.decodeFromString<ProviderSettings>(encoded).extraLogins)
        assertEquals(25, json.decodeFromString<OlderProviderSettings>(encoded).watchHistorySize)
    }

    @Test
    fun `the sync payload carries the extra passwords`() {
        val provider = SyncPayloads.Provider("Name", "http://h", "main", "XTREAM", "", "{}", "pw", mapOf("two" to "pw-two"))
        val received = SyncPayloads.json.decodeFromString<SyncPayloads.Provider>(SyncPayloads.json.encodeToString(provider))
        assertEquals(mapOf("two" to "pw-two"), received.extraPasswords)
    }

    @Test
    fun `a sync payload from an older version has no extra passwords, not an empty map`() {
        val received =
            SyncPayloads.json.decodeFromString<SyncPayloads.Provider>(
                """{"name":"N","url":"http://h","username":"u","type":"XTREAM","config":"","providerSettings":"{}","password":"pw"}""",
            )
        assertNull(received.extraPasswords)
        assertTrue(received.password == "pw")
    }
}
