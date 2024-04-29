package no.risc.infra.connector.models

import no.risc.github.GithubAccessToken
import no.risc.infra.connector.User

data class UserContext(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
    val user: User,
) {
    fun isValid(): Boolean = gcpAccessToken.value.isNotBlank() && githubAccessToken.value.isNotBlank() && user.email.isNotBlank()
}

data class GCPAccessToken(val value: String)

data class Email(
    val value: String,
)
