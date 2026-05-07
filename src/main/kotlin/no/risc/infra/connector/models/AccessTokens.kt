package no.risc.infra.connector.models

data class AccessTokens(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
)

data class RepositoryInfo(
    val defaultBranch: String,
    val hasWriteAccess: Boolean,
)

data class GCPAccessToken(
    val value: String,
)

data class GithubAccessToken(
    val value: String,
)
