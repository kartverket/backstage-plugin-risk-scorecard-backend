package no.risc.infra.connector.models

import no.risc.github.GithubAccessToken
import no.risc.security.User

data class UserContext(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
    val user: User,
) {
    fun isValid(): Boolean = gcpAccessToken.value.isNotBlank() && githubAccessToken.value.isNotBlank() && user.email.value.isNotBlank()
}

data class GCPAccessToken(val value: String)

data class Email(
    val value: String,
)
