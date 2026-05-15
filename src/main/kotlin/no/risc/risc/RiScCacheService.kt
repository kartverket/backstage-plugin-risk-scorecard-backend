package no.risc.risc

import no.risc.risc.models.RiScContentResultDTO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory cache for RiSc fetch results.
 *
 * Results are keyed by `owner/repository/latestSupportedVersion/githubTokenHash` and expire after [cacheTtlSeconds].
 * Scoping the key by a hash of the caller's GitHub access token ensures that cached results from
 * one user are never served to a different user, preventing unintended cross-user data exposure.
 * Cache is invalidated for all versions and all users of a given repository whenever a write operation
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

        /** Returns a short SHA-256 hex digest of [token] for use in cache keys. */
        private fun hashToken(token: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }

    private fun cacheKey(
        owner: String,
        repository: String,
        latestSupportedVersion: String,
        githubAccessToken: String,
    ): String = "$owner/$repository/$latestSupportedVersion/${hashToken(githubAccessToken)}"

    /**
     * Returns the cached list of RiScs if present and not yet expired, otherwise `null`.
     */
    fun get(
        owner: String,
        repository: String,
        latestSupportedVersion: String,
        githubAccessToken: String,
    ): List<RiScContentResultDTO>? {
        if (cacheTtlSeconds <= 0) return null
        val key = cacheKey(owner, repository, latestSupportedVersion, githubAccessToken)
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
     * Stores [results] in the cache for the given [owner]/[repository]/[latestSupportedVersion]/[githubAccessToken] key.
     */
    fun put(
        owner: String,
        repository: String,
        latestSupportedVersion: String,
        githubAccessToken: String,
        results: List<RiScContentResultDTO>,
    ) {
        if (cacheTtlSeconds <= 0) return
        val key = cacheKey(owner, repository, latestSupportedVersion, githubAccessToken)
        cache[key] = CacheEntry(results)
        LOGGER.debug("Cached {} RiScs for {}", results.size, key)
    }

    /**
     * Removes all cached entries for the given [owner]/[repository], regardless of the schema version or user.
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
