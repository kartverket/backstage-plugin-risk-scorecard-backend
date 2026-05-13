package no.risc.security

import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.utils.Repository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Component
@WebFilter(urlPatterns = ["/api/**"])
class AccessTokenValidationFilter(
    private val validationService: ValidationService,
    @Qualifier("handlerExceptionResolver")
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
                    try {
                        Repository(
                            repositoryOwner = request.requestURI.split("/")[3],
                            repositoryName = request.requestURI.split("/")[4],
                        )
                    } catch (e: IndexOutOfBoundsException) {
                        throw InvalidAccessTokensException(
                            "${request.requestURI} does not have access token",
                            cause = e,
                        )
                    }
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
