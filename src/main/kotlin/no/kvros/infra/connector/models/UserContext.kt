package no.kvros.infra.connector.models

import no.kvros.github.GithubAccessToken

data class UserContext(
    val microsoftIdToken: MicrosoftIdToken,
    val githubAccessToken: GithubAccessToken,
    val email: Email
) {
    companion object {
        val INVALID_USER_CONTEXT = UserContext(MicrosoftIdToken(""), GithubAccessToken(""), Email(""))
    }

    fun isValid(): Boolean =
        !microsoftIdToken.value.isBlank() && !githubAccessToken.value.isBlank() && !email.value.isBlank()
}

data class MicrosoftIdToken(val value: String)

data class Email(
    val value: String,
)
