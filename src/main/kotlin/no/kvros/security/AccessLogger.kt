package no.kvros.security

import jakarta.servlet.FilterChain
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
/*
@Component
@WebFilter(filterName = "RequestSaksbehandlerLogg", urlPatterns = ["/api/**"])
class AccessLogger(private val authBrukerService: AuthBrukerService) : OncePerRequestFilter() {

    private val accessLogger: Logger = LoggerFactory.getLogger(AccessLogger::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        accessLogger.info("${request.method.padEnd(4)} ${authBrukerService.brukernavnFraToken()} ${request.requestURI}")
        filterChain.doFilter(request, response)
    }
}
*/