package no.risc.security

import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
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
            if(request.requestURI.startsWith("/api/jira/")){
                filterChain.doFilter(request, response)
                return
            }
            if (request.method.lowercase() != "get") {
                val repository =
                    Repository(
                        repositoryOwner = request.requestURI.split("/")[3],
                        repositoryName = request.requestURI.split("/")[4],
                    )

                runBlocking {
                    validationService.validateAccessTokens(
                        gcpAccessToken = request.getHeader("GCP-Access-Token"),
                        gitHubAccessToken = request.getHeader("GitHub-Access-Token"),
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = repository.repositoryOwner,
                        repositoryName = repository.repositoryName,
                    )
                }
            }
            filterChain.doFilter(request, response)
        } catch (e: IndexOutOfBoundsException) {
            throw InvalidAccessTokensException(
                "${request.requestURI} does not have access token}",
            )
        }
    }
}
