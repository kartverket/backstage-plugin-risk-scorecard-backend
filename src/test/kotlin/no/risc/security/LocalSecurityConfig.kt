package no.risc.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Permissive security configuration for local sandboxed development.
 * All requests are permitted without authentication or token validation.
 * Activated by the `local-sandboxed` Spring profile.
 */
@Configuration
@EnableWebSecurity
@Profile("local-sandboxed")
class LocalSecurityConfig {
    @Bean
    fun localSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.ignoringRequestMatchers("/api/**") }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
