package no.risc.infra.connector.models

import no.risc.github.GithubAccessToken

data class AccessTokens(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
)

data class GCPAccessToken(val value: String)

fun GCPAccessToken.sensor() = GCPAccessToken(
    value = value.slice(IntRange(0, 3))+"****",
)