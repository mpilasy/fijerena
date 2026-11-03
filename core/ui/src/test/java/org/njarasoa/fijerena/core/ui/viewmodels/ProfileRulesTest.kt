package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who may do what to a profile (the profile rules plan, PR-1 to PR-7). */
class ProfileRulesTest {
    private val kid = ProfileActor("kid", kid = true, adminFlag = false)
    private val grownUp = ProfileActor("grown-up", kid = false, adminFlag = false)
    private val admin = ProfileActor("admin", kid = false, adminFlag = true)

    private fun fields(
        name: String = "Name",
        colorIndex: Int = 0,
        admin: Boolean = false,
        autoplay: Boolean = true,
        child: Boolean = false,
        nerd: Boolean = false,
    ) = ProfileFields(name, colorIndex, ProfileSettings(admin, autoplay, child, nerd))

    // PR-1

    @Test
    fun `a kid changes its own colour and play next episode only`() {
        val old = fields(name = "Kid", colorIndex = 1, autoplay = true, child = true)
        val new = fields(name = "Boss", colorIndex = 3, admin = true, autoplay = false, child = false)
        assertEquals(
            fields(name = "Kid", colorIndex = 3, autoplay = false, child = true),
            ProfileRules.update(kid, "kid", old, new),
        )
    }

    @Test
    fun `a kid changes nothing of another profile`() {
        assertNull(ProfileRules.update(kid, "other", fields(), fields(name = "Other")))
        assertFalse(ProfileRules.changesLook(kid, "other"))
    }

    @Test
    fun `a kid changes its own look`() {
        assertTrue(ProfileRules.changesLook(kid, "kid"))
    }

    @Test
    fun `a kid adds and deletes nothing and changes no setup`() {
        assertNull(ProfileRules.add(kid, fields()))
        assertFalse(ProfileRules.delete(kid))
        assertFalse(ProfileRules.changesSetup(kid))
    }

    @Test
    fun `a kid with a stored Admin flag is still a kid`() {
        val adminKid = ProfileActor("kid", kid = true, adminFlag = true)
        assertFalse(adminKid.isAdmin)
        assertNull(ProfileRules.add(adminKid, fields()))
        assertFalse(ProfileRules.delete(adminKid))
    }

    // PR-2

    @Test
    fun `a grown-up does not make itself Admin`() {
        val allowed = ProfileRules.update(grownUp, "grown-up", fields(), fields(admin = true))
        assertFalse(allowed!!.settings.admin)
    }

    @Test
    fun `a grown-up changes its own name colour and play next episode`() {
        val new = fields(name = "New", colorIndex = 2, autoplay = false)
        assertEquals(new, ProfileRules.update(grownUp, "grown-up", fields(), new))
    }

    @Test
    fun `a grown-up changes nothing of another profile`() {
        assertNull(ProfileRules.update(grownUp, "other", fields(), fields(name = "Other")))
        assertFalse(ProfileRules.changesLook(grownUp, "other"))
        assertFalse(ProfileRules.changesSetup(grownUp))
    }

    @Test
    fun `an Admin makes another profile Admin`() {
        val new = fields(admin = true)
        assertEquals(new, ProfileRules.update(admin, "other", fields(), new))
    }

    // PR-4

    @Test
    fun `only an Admin deletes a profile`() {
        assertTrue(ProfileRules.delete(admin))
        assertFalse(ProfileRules.delete(grownUp))
    }

    // PR-5

    @Test
    fun `a grown-up adds a profile, never an Admin`() {
        assertEquals(fields(name = "New", child = true), ProfileRules.add(grownUp, fields(name = "New", child = true)))
        assertFalse(ProfileRules.add(grownUp, fields(admin = true))!!.settings.admin)
    }

    @Test
    fun `an Admin adds an Admin, but never a kid Admin`() {
        assertTrue(ProfileRules.add(admin, fields(admin = true))!!.settings.admin)
        assertFalse(ProfileRules.add(admin, fields(admin = true, child = true))!!.settings.admin)
    }

    // PR-6

    @Test
    fun `a grown-up does not make itself a kid`() {
        val allowed = ProfileRules.update(grownUp, "grown-up", fields(), fields(child = true))
        assertFalse(allowed!!.settings.child)
    }

    @Test
    fun `an Admin makes another profile a kid`() {
        val new = fields(child = true)
        assertEquals(new, ProfileRules.update(admin, "other", fields(), new))
    }

    // PR-7

    @Test
    fun `a kid does not turn off its own Child Profile`() {
        val allowed = ProfileRules.update(kid, "kid", fields(child = true), fields(child = false))
        assertTrue(allowed!!.settings.child)
    }

    @Test
    fun `an Admin turns Child Profile off`() {
        val allowed = ProfileRules.update(admin, "kid", fields(child = true), fields(child = false))
        assertFalse(allowed!!.settings.child)
    }

    // Nerd (the Nerd role plan, RQ-2)

    @Test
    fun `a kid never makes itself a Nerd`() {
        val allowed = ProfileRules.update(kid, "kid", fields(child = true), fields(child = true, nerd = true))
        assertFalse(allowed!!.settings.nerd)
    }

    @Test
    fun `an Admin does not make a kid a Nerd`() {
        val allowed = ProfileRules.update(admin, "kid", fields(child = true), fields(child = true, nerd = true))
        assertFalse(allowed!!.settings.nerd)
    }

    @Test
    fun `an Admin who turns Child on also turns Nerd off`() {
        val allowed = ProfileRules.update(admin, "other", fields(nerd = true), fields(child = true, nerd = true))
        assertTrue(allowed!!.settings.child)
        assertFalse(allowed.settings.nerd)
    }

    @Test
    fun `an Admin sets Nerd on any grown-up`() {
        assertTrue(ProfileRules.update(admin, "other", fields(), fields(nerd = true))!!.settings.nerd)
        assertFalse(ProfileRules.update(admin, "other", fields(nerd = true), fields(nerd = false))!!.settings.nerd)
        assertTrue(ProfileRules.update(admin, "admin", fields(), fields(nerd = true))!!.settings.nerd)
    }

    @Test
    fun `a grown-up sets its own Nerd`() {
        assertTrue(ProfileRules.update(grownUp, "grown-up", fields(), fields(nerd = true))!!.settings.nerd)
        assertFalse(ProfileRules.update(grownUp, "grown-up", fields(nerd = true), fields(nerd = false))!!.settings.nerd)
    }

    @Test
    fun `a grown-up does not set another profile's Nerd`() {
        assertNull(ProfileRules.update(grownUp, "other", fields(), fields(nerd = true)))
    }

    @Test
    fun `a Nerd alone gets no right to manage`() {
        // The actor's own Nerd flag is not part of ProfileActor: a Nerd who is not an Admin is a grown-up.
        assertFalse(ProfileRules.delete(grownUp))
        assertFalse(ProfileRules.changesSetup(grownUp))
        assertFalse(ProfileRules.update(grownUp, "grown-up", fields(nerd = true), fields(nerd = true, admin = true))!!.settings.admin)
    }

    @Test
    fun `only an Admin adds a Nerd, and never a kid Nerd`() {
        assertFalse(ProfileRules.add(grownUp, fields(nerd = true))!!.settings.nerd)
        assertTrue(ProfileRules.add(admin, fields(nerd = true))!!.settings.nerd)
        assertFalse(ProfileRules.add(admin, fields(nerd = true, child = true))!!.settings.nerd)
    }
}
