package no.kvros.infra.connector.models

import no.kvros.github.GithubAccessToken

data class UserContext(
    val microsoftIdToken: MicrosoftIdToken,
    val githubAccessToken: GithubAccessToken,
    val email: Email
)

data class MicrosoftIdToken(val value: String)

data class Email(
    val value: String,
)
