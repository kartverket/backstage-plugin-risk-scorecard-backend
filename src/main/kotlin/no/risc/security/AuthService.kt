package no.risc.security

import no.risc.infra.connector.models.Email
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

object AuthService {
    fun getUser(): User = User(getEmail(), getName())

    fun getName(): String =
        SecurityContextHolder.getContext().authentication.credentials.let {
            when (it) {
                is Jwt -> (it.claims["sub"] as String).split("/").last()
                else -> throw Exception("No JWT found in security context.")
            }
        }

    private fun getEmail(): Email =
        SecurityContextHolder.getContext().authentication.credentials.let {
            when (it) {
                is Jwt -> Email((it.claims["sub"] as String).split("/").last().replace('_', '@'))
                else -> throw Exception("No JWT found in security context.")
            }
        }
}

data class User(
    val email: Email,
    val name: String,
)
