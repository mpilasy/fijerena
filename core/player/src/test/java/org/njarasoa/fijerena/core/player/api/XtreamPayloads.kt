package org.njarasoa.fijerena.core.player.api

/**
 * The bodies this provider actually returns, including its several ways of saying "nothing".
 *
 * Each of these cost a debugging session before it was recognised — see the commits named beside
 * them. Keeping them named means the next reader of a parsing change can feed it what the wire
 * really carries instead of a body shaped like the model.
 */
object XtreamPayloads {
    /** `[]` where an object belongs: how these servers say "no such id" (`4220ce69`). */
    const val EMPTY_ARRAY = "[]"

    /** Well-formed, no payload — a proxy answering before it has the data (`72eb2e90`). */
    const val EMPTY_OBJECT = "{}"

    /** Seasons and episodes keys present, both empty. Same meaning as [EMPTY_OBJECT]. */
    const val SERIES_NO_EPISODES = """{"seasons":[],"episodes":{}}"""

    /**
     * An info object with no episode list. Deliberately read as a real show with nothing under it,
     * not as unavailable — nothing here separates that from a show the catalogue genuinely lists
     * empty. Decided 2026-08-19; see `plans/id-type-safety-plan.md`.
     */
    const val SERIES_INFO_ONLY = """{"info":{"name":"EN - Law & Order (1990) (US)"},"episodes":{}}"""

    /** Episodes with no info object at all — still a usable answer. */
    const val SERIES_EPISODES_ONLY =
        """{"episodes":{"1":[{"id":"242136","episode_num":18,"title":"S06E18","container_extension":"mkv"}]}}"""

    /**
     * Series info whose `info` omits `name`, or sends it as `title`. It was the one required field
     * in the model, so its absence threw away the whole episode list (`1975d0ab`).
     */
    const val SERIES_INFO_WITHOUT_NAME =
        """{"info":{"title":"EN - Law & Order (1990) (US)"},"episodes":{"1":[""" +
            """{"id":"242136","episode_num":18,"title":"S06E18","container_extension":"mkv"}]}}"""

    /**
     * Two episodes, one of them missing `container_extension` — a required field on the model,
     * and the one the stream URL is built from, so that episode is unplayable either way. Only it
     * is dropped; the readable episode survives. Before `EpisodesMapSerializer` decoded episodes
     * one at a time, the bad one took the whole show's list with it.
     */
    const val SERIES_ONE_EPISODE_MALFORMED =
        """{"episodes":{"1":[""" +
            """{"id":"242136","episode_num":18,"title":"S06E18","container_extension":"mkv"},""" +
            """{"id":"242137","episode_num":19,"title":"S06E19"}]}}"""

    /** Not JSON at all — a gateway page in place of the API. */
    const val NOT_JSON = "<html>gateway timeout</html>"

    /** A movie answer with stream data but no metadata: enough to play, not enough to describe. */
    const val VOD_MOVIE_DATA_ONLY = """{"movie_data":{"stream_id":149880,"name":"BlacKkKlansman (2018)"}}"""
}
