package no.risc.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.risc.github.RiScMainAndBranchContentWithLastPublishedInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig {
    @Bean
    fun encryptedRiScContentCache(
        @Value("\${cache.risc.ttl-minutes:5}") ttlMinutes: Long,
        @Value("\${cache.risc.max-size:500}") maxSize: Long,
    ): Cache<String, RiScMainAndBranchContentWithLastPublishedInfo> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .maximumSize(maxSize)
            .build()
}
