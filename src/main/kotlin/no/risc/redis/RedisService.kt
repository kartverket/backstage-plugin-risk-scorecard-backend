package no.risc.redis

import no.risc.exception.exceptions.InitializeRiScSessionNotFoundException
import no.risc.redis.model.InitializeRiScSession
import no.risc.utils.Hasher.sha256
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

data class Repository(
    val owner: String,
    val repository: String,
) {
    fun sha256() = "$owner$repository".sha256()
}

@Service
class RedisService(
    private val initializeRiScSessionRedisClient: RedisTemplate<String, InitializeRiScSession>,
) {
    fun storeInitializeRiScSession(
        ttlSeconds: Int = 60,
        repository: Repository,
        gcpAccessTokenValue: String,
    ) {
        val initializeRiScSession =
            InitializeRiScSession(
                repositoryHash = repository.sha256(),
                gcpAccessTokenValue = gcpAccessTokenValue,
            )
        initializeRiScSessionRedisClient.opsForValue().set(initializeRiScSession.repositoryHash, initializeRiScSession)
        initializeRiScSessionRedisClient.expire(initializeRiScSession.repositoryHash, ttlSeconds.toLong(), TimeUnit.SECONDS)
    }

    fun retrieveInitializeRiScSessionByRepository(
        deleteOnRetrieve: Boolean = true,
        repository: Repository,
    ): InitializeRiScSession {
        val initializeRiScSession = initializeRiScSessionRedisClient.opsForValue().get(repository.sha256())
        if (deleteOnRetrieve) {
            initializeRiScSessionRedisClient.delete(repository.sha256())
        }
        return initializeRiScSession
            ?: throw InitializeRiScSessionNotFoundException(
                "The session details for initializing a RiSc for " +
                    "${repository.owner}/${repository.repository} could not be found",
            )
    }
}
