package no.risc.exception.exceptions

data class PermissionDeniedOnGitHubException(
    override val message: String?,
) : Exception()
