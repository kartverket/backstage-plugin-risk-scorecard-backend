package no.kvros.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuerUri: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { it.requestMatchers("/actuator/health").permitAll() }
            .authorizeHttpRequests { it.requestMatchers("/api/ros/schemas/latest").permitAll() }
            .authorizeHttpRequests { it.requestMatchers("api/**").authenticated() }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.decoder(jwtDecoder()) } }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource? {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = getAllowedOrigins()
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders =
            listOf(
                "authorization",
                "content-type",
                "x-auth-token",
                "x-request-id",
                "contenttype",
                "Content-Type",
                "Authorization",
                "Microsoft-Id-Token",
                "GCP-Access-Token",
            )
        configuration.exposedHeaders = mutableListOf("x-auth-token")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    fun getAllowedOrigins(): List<String> {
        return listOf(
            "http://localhost:3000/",
            "https://kv-ros-backstage-245zlcbrnq-lz.a.run.app",
        )
    }
}
