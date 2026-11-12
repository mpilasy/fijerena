package org.njarasoa.fijerena.core.ui.viewmodels

/**
 * The profile in use, as [ProfileRules] sees it: whether it is a kid (Child Profile on) and
 * whether it is an Admin. A kid is never an Admin, whatever its stored flag says.
 */
data class ProfileActor(
    val id: String,
    val kid: Boolean,
    private val adminFlag: Boolean,
) {
    val isAdmin: Boolean get() = adminFlag && !kid
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
 *   background, play next episode and track languages. It adds and deletes nothing, and is never a Nerd.
 * - A grown-up who is not an Admin changes its own name, colour, personal settings and Nerd, and
 *   adds a profile (a kid or not, never an Admin or a Nerd). It does not make itself Admin or a
 *   kid, and does not change another profile's Nerd.
 * - An Admin changes any profile, Admin, Child Profile and Nerd included, and deletes
 *   profiles.
 * - Nerd alone gives no right to manage: it only shows the stats and extra information.
 *
 * The other checks (the last Admin, the profile in use, the last profile) stay where they are.
 */
object ProfileRules {
    /** The profile to add, Admin and Nerd off unless an Admin adds it; null when [actor] may not add. */
    fun add(
        actor: ProfileActor,
        fields: ProfileFields,
    ): ProfileFields? =
        if (actor.kid) {
            null
        } else {
            val settings = fields.settings
            fields.copy(
                settings =
                    settings.copy(
                        admin = actor.isAdmin && settings.admin && !settings.child,
                        nerd = actor.isAdmin && settings.nerd && !settings.child,
                    ),
            )
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
            actor.isAdmin -> {
                // A kid is never a Nerd, also when an Admin turns Child on in the same change.
                new.copy(settings = new.settings.copy(nerd = new.settings.nerd && !new.settings.child))
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
                new.copy(settings = new.settings.copy(admin = old.settings.admin, child = old.settings.child))
            }
        }

    /** Only an Admin deletes a profile. */
    fun delete(actor: ProfileActor): Boolean = actor.isAdmin

    /** The colour theme, UI Theme, Look and Feel, poster background and track languages of [targetId]: its own, or an Admin's. */
    fun changesLook(
        actor: ProfileActor,
        targetId: String,
    ): Boolean = actor.isAdmin || targetId == actor.id

    /** A profile's sources, content filters and a child's favourites: an Admin's only. */
    fun changesSetup(actor: ProfileActor): Boolean = actor.isAdmin
}
