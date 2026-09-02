package org.njarasoa.fijerena.core.network

/**
 * In-memory cache with a fixed time-to-live per entry, capped at [maxSize] entries. Not
 * persisted across process death — for detail data (movie/series metadata, content ratings)
 * that's expensive to (re)fetch (network + TMDB calls) but rarely changes, so a reopen within
 * the same app session shouldn't hit the network again.
 *
 * TTL alone only evicts a key when it's read again — a title opened once and never revisited
 * would otherwise sit here for the full TTL. The size cap (LRU, evicted on insert) bounds that
 * over a long session browsing a large catalogue.
 */
class TtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

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
