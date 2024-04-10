package no.kvros.infra.connector.models

import no.kvros.github.GithubAccessToken
import no.kvros.security.MicrosoftUser

data class UserContext(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
    val microsoftUser: MicrosoftUser
) {
    fun isValid(): Boolean =
        gcpAccessToken.value.isNotBlank() && githubAccessToken.value.isNotBlank() && microsoftUser.email.value.isNotBlank()
}

data class GCPAccessToken(val value: String)

data class Email(
    val value: String,
)
