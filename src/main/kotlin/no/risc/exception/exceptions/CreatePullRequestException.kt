package no.risc.exception.exceptions

data class CreatePullRequestException (
    override val message: String,
    val riScId: String,
) : Exception()