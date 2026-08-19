package org.njarasoa.fijerena.core.network

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache with a fixed time-to-live per entry. Not persisted across process death —
 * for detail data (movie/series metadata, content ratings) that's expensive to (re)fetch
 * (network + TMDB calls) but rarely changes, so a reopen within the same app session shouldn't
 * hit the network again.
 */
class TtlCache<K : Any, V : Any>(private val ttlMs: Long) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(
        key: K,
        value: V,
    ) {
        map[key] = Entry(value, System.currentTimeMillis() + ttlMs)
    }

    /** Drops [key], so the next read misses and refetches — used by explicit refresh actions. */
    fun remove(key: K) {
        map.remove(key)
    }
}
