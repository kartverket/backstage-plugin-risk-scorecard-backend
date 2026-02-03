package no.risc.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("RiSc (Risk Scorecard) API")
                    .version("1.0.0")
                    .description(
                        """
                        REST API for managing Risk Scorecards (RiSc) stored in GitHub repositories.
                        This API provides endpoints for creating, reading, updating, and deleting risk assessments,
                        as well as integration with Google Cloud Platform and initialization services.
                        """.trimIndent(),
                    ).contact(
                        Contact()
                            .name("Kartverket")
                            .url("https://github.com/kartverket"),
                    ).license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT"),
                    ),
            ).components(
                Components()
                    .addSecuritySchemes(
                        "Bearer",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description(
                                "JWT token for OAuth2 authentication (Required for all API requests)<br />NOTE: the 'Bearer'-part is prepended by Swagger, so don't paste with that here",
                            ),
                    ),
            ).addSecurityItem(
                SecurityRequirement()
                    .addList("Bearer"),
            )
}
