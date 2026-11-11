package org.njarasoa.fijerena.core.network

/**
 * In-memory cache with a fixed time-to-live per entry, capped at [maxSize] entries, for detail
 * data that is costly to fetch (network and TMDB) but rarely changes: a reopen in the same session
 * asks the network nothing. Not kept across process death.
 *
 * A TTL evicts a key only when it is read again, so the size cap (LRU, evicted on insert) bounds a
 * long session in a large catalogue.
 */
class TtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private data class Entry<V>(
        val value: V,
        val expiresAt: Long,
    )

    private val map =
        object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>) = size > maxSize
        }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(
        key: K,
        value: V,
    ) {
        map[key] = Entry(value, System.currentTimeMillis() + ttlMs)
    }

    /** Drops [key], so the next read misses and refetches — used by explicit refresh actions. */
    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }

    /** Drops every entry — used when the system signals memory pressure. */
    @Synchronized
    fun clear() {
        map.clear()
    }

    private companion object {
        const val DEFAULT_MAX_SIZE = 200
    }
}
