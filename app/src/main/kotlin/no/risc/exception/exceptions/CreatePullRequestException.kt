package no.risc.exception.exceptions

data class CreatePullRequestException(
    override val message: String,
) : Exception()
