package org.njarasoa.fijerena.core.network.queue

interface RefreshTask : Comparable<RefreshTask> {
    /** Unique id of the task, for deduplication ("epg_refresh", "xtream_streams_123"). */
    val id: String

    /** Priority of the task, higher first: a RefreshPriority constant. */
    val priority: Int

    /** Runs the task, on a background thread. */
    suspend fun execute()

    override fun compareTo(other: RefreshTask): Int {
        // Higher priority first
        return other.priority.compareTo(this.priority)
    }
}

object RefreshPriority {
    const val HIGH = 100 // Live TV Channels
    const val MEDIUM = 50 // EPG
    const val LOW = 10 // VOD / Series
}
