package no.kvros.infra.connector.models

import no.kvros.github.GithubAccessToken
import no.kvros.security.MicrosoftUser

data class UserContext(
    val microsoftIdToken: MicrosoftIdToken,
    val githubAccessToken: GithubAccessToken,
    val microsoftUser: MicrosoftUser
) {
    fun isValid(): Boolean =
        microsoftIdToken.value.isNotBlank() && githubAccessToken.value.isNotBlank() && microsoftUser.email.value.isNotBlank()
}

data class MicrosoftIdToken(val value: String)

data class Email(
    val value: String,
)
