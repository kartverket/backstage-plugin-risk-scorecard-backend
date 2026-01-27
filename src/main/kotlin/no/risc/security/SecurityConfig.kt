package no.risc.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.lang.Thread.sleep

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val environment: Environment,
) {
    private val logger: Logger = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    @Order(0)
    fun jiraSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/jira/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }

        return http.build()
    }

    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }

        return http.build()
    }

    @Bean
    @Order(2)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { it.anyRequest().permitAll() }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
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
                "GitHub-Access-Token",
            )
        configuration.exposedHeaders = mutableListOf("x-auth-token")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtDecoder(): JwtDecoder? {
        val issuerUri = environment.getProperty("ISSUER_URI")
        require(!issuerUri.isNullOrBlank()) { "ISSUER_URI must be configured in the environment" }

        logger.info("Starting build of JwtDecoder using ISSUER_URI=$issuerUri")
        val millis = 60_000L

        while (true) {
            try {
                val jwtDecoder =
                    NimbusJwtDecoder
                        .withIssuerLocation(issuerUri)
                        .jwtProcessorCustomizer { customizer ->
                            customizer.jwsTypeVerifier =
                                DefaultJOSEObjectTypeVerifier(
                                    JOSEObjectType.JOSE_JSON,
                                    JOSEObjectType.JWT,
                                    JOSEObjectType.JOSE,
                                    // required for tokens issued by Backstage
                                    JOSEObjectType("vnd.backstage.user"),
                                    null,
                                )
                        }.build()
                logger.info("JwtDecoder successfully instantiated")
                return JwtDecoder { token -> jwtDecoder.decode(token) }
            } catch (e: Exception) {
                logger
                    .error(
                        "Could not instantiate JwtDecoder. Retrying in ${millis / 1000} seconds...",
                        e,
                    )
                sleep(millis)
            }
        }
    }

    fun getAllowedOrigins(): List<String> =
        listOf(
            "http://localhost:3000/",
            "https://sandbox.kartverket.dev",
            "https://backstage.atgcp1-dev.kartverket-intern.cloud/",
            "https://kartverket.dev",
            "https://risc-457384642040.europe-north1.run.app",
        )
}
