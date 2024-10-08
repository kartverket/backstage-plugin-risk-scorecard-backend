package no.risc.redis.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("InitRiScSession")
data class InitializeRiScSession(
    @Id val repositoryHash: String,
    val gcpAccessTokenValue: String,
)

