package no.risc.risc

import no.risc.risc.models.RiScContentResultDTO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory cache for RiSc fetch results.
 *
 * Results are keyed by `owner/repository/latestSupportedVersion` and expire after [cacheTtlSeconds].
 * Cache is invalidated for all versions of a given repository whenever a write operation
 * (create, update, delete, publish) is performed on that repository.
 *
 * TTL is configurable via the `risc.cache.ttl-seconds` property.
 */
@Service
class RiScCacheService(
    @Value("\${risc.cache.ttl-seconds}") private val cacheTtlSeconds: Long,
) {
    private data class CacheEntry(
        val results: List<RiScContentResultDTO>,
        val cachedAt: Instant = Instant.now(),
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RiScCacheService::class.java)
    }

    private fun cacheKey(
        owner: String,
        repository: String,
        latestSupportedVersion: String,
    ): String = "$owner/$repository/$latestSupportedVersion"

    /**
     * Returns the cached list of RiScs if present and not yet expired, otherwise `null`.
     */
    fun get(
        owner: String,
        repository: String,
        latestSupportedVersion: String,
    ): List<RiScContentResultDTO>? {
        if (cacheTtlSeconds <= 0) return null
        val key = cacheKey(owner, repository, latestSupportedVersion)
        val entry = cache[key] ?: return null
        val ageSeconds = Duration.between(entry.cachedAt, Instant.now()).seconds
        return if (ageSeconds < cacheTtlSeconds) {
            LOGGER.debug("Cache HIT for {} (age: {}s / ttl: {}s)", key, ageSeconds, cacheTtlSeconds)
            entry.results
        } else {
            LOGGER.debug("Cache EXPIRED for {} (age: {}s / ttl: {}s)", key, ageSeconds, cacheTtlSeconds)
            cache.remove(key)
            null
        }
    }

    /**
     * Stores [results] in the cache for the given [owner]/[repository]/[latestSupportedVersion] key.
     */
    fun put(
        owner: String,
        repository: String,
        latestSupportedVersion: String,
        results: List<RiScContentResultDTO>,
    ) {
        if (cacheTtlSeconds <= 0) return
        val key = cacheKey(owner, repository, latestSupportedVersion)
        cache[key] = CacheEntry(results)
        LOGGER.debug("Cached {} RiScs for {}", results.size, key)
    }

    /**
     * Removes all cached entries for the given [owner]/[repository], regardless of the schema version.
     * Should be called after any write operation (create, update, delete, publish).
     */
    fun invalidate(
        owner: String,
        repository: String,
    ) {
        val prefix = "$owner/$repository/"
        val removed = cache.keys.filter { it.startsWith(prefix) }
        removed.forEach { cache.remove(it) }
        if (removed.isNotEmpty()) {
            LOGGER.debug("Invalidated {} cache entries for {}/{}", removed.size, owner, repository)
        }
    }
}

