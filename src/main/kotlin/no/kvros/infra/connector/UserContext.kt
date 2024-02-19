package no.kvros.infra.connector

import no.kvros.github.GithubAppAccessToken

data class UserContext(
    val microsoftAccessToken: MicrosoftAccessToken,
    val githubAccessToken: GithubAppAccessToken
)

data class MicrosoftAccessToken(val value: String)

data class Email(
    val value: String,
)
