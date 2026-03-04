package no.risc.exception.exceptions

import no.risc.risc.models.ProcessRiScResultDTO

data class GitHubFetchException(
    override val message: String,
    val response: ProcessRiScResultDTO,
) : Exception()
