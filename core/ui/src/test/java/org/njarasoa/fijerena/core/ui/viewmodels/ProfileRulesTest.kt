package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who may do what to a profile (the profile rules plan, PR-1 to PR-7). */
class ProfileRulesTest {
    private val kid = ProfileActor("kid", kid = true, expertFlag = false)
    private val grownUp = ProfileActor("grown-up", kid = false, expertFlag = false)
    private val expert = ProfileActor("expert", kid = false, expertFlag = true)

    private fun fields(
        name: String = "Name",
        colorIndex: Int = 0,
        devMode: Boolean = false,
        autoplay: Boolean = true,
        child: Boolean = false,
    ) = ProfileFields(name, colorIndex, ProfileSettings(devMode, autoplay, child))

    // PR-1

    @Test
    fun `a kid changes its own colour and play next episode only`() {
        val old = fields(name = "Kid", colorIndex = 1, autoplay = true, child = true)
        val new = fields(name = "Boss", colorIndex = 3, devMode = true, autoplay = false, child = false)
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
    fun `a kid with a stored Expert flag is still a kid`() {
        val expertKid = ProfileActor("kid", kid = true, expertFlag = true)
        assertFalse(expertKid.isExpert)
        assertNull(ProfileRules.add(expertKid, fields()))
        assertFalse(ProfileRules.delete(expertKid))
    }

    // PR-2

    @Test
    fun `a grown-up does not make itself Expert`() {
        val allowed = ProfileRules.update(grownUp, "grown-up", fields(), fields(devMode = true))
        assertFalse(allowed!!.settings.devMode)
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
    fun `an Expert makes another profile Expert`() {
        val new = fields(devMode = true)
        assertEquals(new, ProfileRules.update(expert, "other", fields(), new))
    }

    // PR-4

    @Test
    fun `only an Expert deletes a profile`() {
        assertTrue(ProfileRules.delete(expert))
        assertFalse(ProfileRules.delete(grownUp))
    }

    // PR-5

    @Test
    fun `a grown-up adds a profile, never an Expert`() {
        assertEquals(fields(name = "New", child = true), ProfileRules.add(grownUp, fields(name = "New", child = true)))
        assertFalse(ProfileRules.add(grownUp, fields(devMode = true))!!.settings.devMode)
    }

    @Test
    fun `an Expert adds an Expert, but never a kid Expert`() {
        assertTrue(ProfileRules.add(expert, fields(devMode = true))!!.settings.devMode)
        assertFalse(ProfileRules.add(expert, fields(devMode = true, child = true))!!.settings.devMode)
    }

    // PR-6

    @Test
    fun `a grown-up does not make itself a kid`() {
        val allowed = ProfileRules.update(grownUp, "grown-up", fields(), fields(child = true))
        assertFalse(allowed!!.settings.child)
    }

    @Test
    fun `an Expert makes another profile a kid`() {
        val new = fields(child = true)
        assertEquals(new, ProfileRules.update(expert, "other", fields(), new))
    }

    // PR-7

    @Test
    fun `a kid does not turn off its own Child Profile`() {
        val allowed = ProfileRules.update(kid, "kid", fields(child = true), fields(child = false))
        assertTrue(allowed!!.settings.child)
    }

    @Test
    fun `an Expert turns Child Profile off`() {
        val allowed = ProfileRules.update(expert, "kid", fields(child = true), fields(child = false))
        assertFalse(allowed!!.settings.child)
    }
}
