package no.risc.redis

import no.risc.redis.model.InitializeRiScSession
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class RedisClient {
    @Bean
    fun initializeRiScSessionRedisClient(jedisConnectionFactory: JedisConnectionFactory): RedisTemplate<String, InitializeRiScSession> {
        val redisTemplate = RedisTemplate<String, InitializeRiScSession>()
        redisTemplate.connectionFactory = jedisConnectionFactory
        return redisTemplate
    }
}
