package no.risc.infra.connector.models

import no.risc.github.GithubAccessToken
import no.risc.security.MicrosoftUser

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
