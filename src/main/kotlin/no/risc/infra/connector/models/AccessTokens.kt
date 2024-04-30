package no.risc.infra.connector.models

import no.risc.github.GithubAccessToken

data class AccessTokens(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
) {
    fun isValid(): Boolean = gcpAccessToken.value.isNotBlank() && githubAccessToken.value.isNotBlank()
}

data class GCPAccessToken(val value: String)
