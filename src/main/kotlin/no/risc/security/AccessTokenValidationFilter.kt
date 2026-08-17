package no.risc.security

import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import no.risc.utils.Repository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Component
@WebFilter(urlPatterns = ["/api/**"])
@Profile("!(local-sandboxed)")
class AccessTokenValidationFilter(
    private val validationService: ValidationService,
    private val handlerExceptionResolver: HandlerExceptionResolver,
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
                runBlocking {
                    validationService.validateAccessTokens(
                        gcpAccessToken = request.getHeader("GCP-Access-Token"),
                        gitHubAccessToken = request.getHeader("GitHub-Access-Token"),
                        repositoryOwner = repository.repositoryOwner,
                        repositoryName = repository.repositoryName,
                    )
                }
            }
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            handlerExceptionResolver.resolveException(request, response, null, e)
        }
    }
}
