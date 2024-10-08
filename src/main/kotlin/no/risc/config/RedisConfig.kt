package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import kotlin.properties.Delegates

@Configuration
@ConfigurationProperties(prefix = "redis")
class RedisConfig {

    lateinit var hostname: String
    var port by Delegates.notNull<Int>()
    lateinit var username: String
    lateinit var password: String

    @Bean
    fun jedisConnectionFactory(
        redisConfig: RedisConfig
    ): JedisConnectionFactory {
        val redisClientConfig = RedisStandaloneConfiguration().apply {
            this.hostName = redisConfig.hostname
            this.port = redisConfig.port
            this.username = redisConfig.username
            this.password = RedisPassword.of(redisConfig.password)
        }
        return JedisConnectionFactory(redisClientConfig)
    }
}
