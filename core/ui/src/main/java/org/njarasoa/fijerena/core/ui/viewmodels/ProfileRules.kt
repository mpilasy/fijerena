package org.njarasoa.fijerena.core.ui.viewmodels

/**
 * The profile in use, as [ProfileRules] sees it: whether it is a kid (Child Profile on) and
 * whether it has Expert mode. A kid is never an Expert, whatever its stored flag says.
 */
data class ProfileActor(
    val id: String,
    val kid: Boolean,
    private val expertFlag: Boolean,
) {
    val isExpert: Boolean get() = expertFlag && !kid
}

/** A profile's fields the rules govern: its name, its colour and its own settings. */
data class ProfileFields(
    val name: String,
    val colorIndex: Int,
    val settings: ProfileSettings,
)

/**
 * Who may do what to a profile (the profile rules plan, PR-1 to PR-7). Pure: [ProfilesViewModel]
 * applies it below the screens, which also hide what is not allowed.
 *
 * - A kid changes only its own personal settings: colour, UI Theme, Theme, Look and Feel, poster
 *   background and play next episode. It adds and deletes nothing.
 * - A grown-up without Expert mode changes its own name, colour and personal settings, and adds a
 *   profile (a kid or not, never an Expert). It does not make itself Expert or a kid.
 * - An Expert changes any profile, Expert mode and Child Profile included, and deletes profiles.
 *
 * The other checks (the last Expert, the profile in use, the last profile) stay where they are.
 */
object ProfileRules {
    /** The profile to add, Expert mode off unless an Expert adds it; null when [actor] may not add. */
    fun add(
        actor: ProfileActor,
        fields: ProfileFields,
    ): ProfileFields? =
        if (actor.kid) {
            null
        } else {
            val settings = fields.settings
            fields.copy(settings = settings.copy(devMode = actor.isExpert && settings.devMode && !settings.child))
        }

    /**
     * The fields to store for profile [targetId]: [new] where [actor] may change a field, [old]
     * where it may not. Null when [actor] may change nothing of that profile.
     */
    fun update(
        actor: ProfileActor,
        targetId: String,
        old: ProfileFields,
        new: ProfileFields,
    ): ProfileFields? =
        when {
            actor.isExpert -> {
                new
            }

            targetId != actor.id -> {
                null
            }

            actor.kid -> {
                ProfileFields(
                    name = old.name,
                    colorIndex = new.colorIndex,
                    settings = old.settings.copy(autoplayNextEpisode = new.settings.autoplayNextEpisode),
                )
            }

            else -> {
                new.copy(settings = new.settings.copy(devMode = old.settings.devMode, child = old.settings.child))
            }
        }

    /** Only an Expert deletes a profile. */
    fun delete(actor: ProfileActor): Boolean = actor.isExpert

    /** The colour theme, UI Theme, Look and Feel and poster background of [targetId]: its own, or an Expert's. */
    fun changesLook(
        actor: ProfileActor,
        targetId: String,
    ): Boolean = actor.isExpert || targetId == actor.id

    /** A profile's sources, content filters and a child's favourites: an Expert's only. */
    fun changesSetup(actor: ProfileActor): Boolean = actor.isExpert
}
