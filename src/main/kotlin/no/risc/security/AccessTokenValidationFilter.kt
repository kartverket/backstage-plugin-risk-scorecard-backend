package no.risc.security

import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.infra.connector.models.GitHubPermission
import no.risc.utils.Repository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@WebFilter(urlPatterns = ["/api/**"])
class AccessTokenValidationFilter(
    private val validationService: ValidationService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            if (request.method.lowercase() != "get") {
                val repository =
                    Repository(
                        repositoryOwner = request.requestURI.split("/")[3],
                        repositoryName = request.requestURI.split("/")[4],
                    )

                validationService.validateAccessTokens(
                    request.getHeader("GCP-Access-Token"),
                    request.getHeader("GitHub-Access-Token"),
                    GitHubPermission.WRITE,
                    repository.repositoryOwner,
                    repository.repositoryName,
                )
            }
            filterChain.doFilter(request, response)
        } catch (e: IndexOutOfBoundsException) {
            throw InvalidAccessTokensException(
                "${request.requestURI} does not have access token}",
            )
        }
    }
}
