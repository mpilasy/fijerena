package org.njarasoa.fijerena.core.player.api

/**
 * The bodies this provider really returns, including its several ways to say "nothing". Each one
 * cost a debugging session; feed a parsing change these, not a body shaped like the model.
 */
object XtreamPayloads {
    /** `[]` where an object belongs: how these servers say "no such id". */
    const val EMPTY_ARRAY = "[]"

    /** Well-formed, no payload: a proxy that answers before it has the data. */
    const val EMPTY_OBJECT = "{}"

    /** Seasons and episodes keys present, both empty. Same meaning as [EMPTY_OBJECT]. */
    const val SERIES_NO_EPISODES = """{"seasons":[],"episodes":{}}"""

    /**
     * An info object with no episode list. Read as a real show with nothing under it, not as
     * unavailable: nothing separates it from a show the catalogue lists empty. Decided in the id
     * type-safety plan.
     */
    const val SERIES_INFO_ONLY = """{"info":{"name":"EN - Law & Order (1990) (US)"},"episodes":{}}"""

    /** Episodes with no info object at all — still a usable answer. */
    const val SERIES_EPISODES_ONLY =
        """{"episodes":{"1":[{"id":"242136","episode_num":18,"title":"S06E18","container_extension":"mkv"}]}}"""

    /**
     * Series info whose `info` leaves out `name`, or sends it as `title`. It was the model's one
     * required field, so its absence threw away the whole episode list.
     */
    const val SERIES_INFO_WITHOUT_NAME =
        """{"info":{"title":"EN - Law & Order (1990) (US)"},"episodes":{"1":[""" +
            """{"id":"242136","episode_num":18,"title":"S06E18","container_extension":"mkv"}]}}"""

    /**
     * Two episodes, one without `container_extension` (a required field, and the one the stream URL
     * is built from, so that episode cannot play). Only it is dropped; the readable episode stays.
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
