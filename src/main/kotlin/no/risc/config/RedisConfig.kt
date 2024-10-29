package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import kotlin.properties.Delegates

@Configuration
@ConfigurationProperties(prefix = "redis")
class RedisConfig {
    lateinit var hostname: String
    var port by Delegates.notNull<Int>()

    @Bean
    fun jedisConnectionFactory(redisConfig: RedisConfig): JedisConnectionFactory {
        val redisClientConfig =
            RedisStandaloneConfiguration().apply {
                this.hostName = redisConfig.hostname
                this.port = redisConfig.port
            }
        return JedisConnectionFactory(redisClientConfig)
    }
}
