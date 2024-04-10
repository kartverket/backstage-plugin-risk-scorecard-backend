package no.kvros.security

import no.kvros.infra.connector.models.Email
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

object AuthService {

    fun getMicrosoftUser(): MicrosoftUser = MicrosoftUser(getEmail(), getName())

    fun getName(): String =
        SecurityContextHolder.getContext().authentication.credentials.let {
            when (it) {
                is Jwt -> it.claims["name"] as String
                else   -> throw Exception("No JWT found in security context.")
            }
        }

    private fun getEmail(): Email =
        SecurityContextHolder.getContext().authentication.credentials.let {
            when (it) {
                is Jwt -> Email(it.claims["email"] as String)
                else   -> throw Exception("No JWT found in security context.")
            }
        }
}

data class MicrosoftUser(
    val email: Email, val name: String
)