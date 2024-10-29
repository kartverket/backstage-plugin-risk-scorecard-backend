package no.risc.infra.connector.models

data class AccessTokens(
    val githubAccessToken: GithubAccessToken,
    val gcpAccessToken: GCPAccessToken,
)

enum class GitHubPermission {
    READ,
    WRITE,
}

data class RepositoryInfo(
    val defaultBranch: String,
    val permissions: List<GitHubPermission>,
)

data class GCPAccessToken(
    val value: String,
)

data class GithubAccessToken(
    val value: String,
)
