package org.njarasoa.fijerena.core.network.queue

/**
 * Interface representing a task to be executed in the refresh queue.
 */
interface RefreshTask : Comparable<RefreshTask> {
    /**
     * Unique identifier for the task, used to deduplicate tasks.
     * e.g. "epg_refresh", "xtream_streams_123"
     */
    val id: String

    /**
     * Priority of the task. Higher value means higher priority.
     * Use constants from RefreshPriority.
     */
    val priority: Int

    /**
     * Execute the task. This will be called on a background thread.
     */
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
