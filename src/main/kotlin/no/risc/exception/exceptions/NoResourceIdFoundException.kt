package no.risc.exception.exceptions

import no.risc.risc.ProcessRiScResultDTO

data class NoResourceIdFoundException(
    override val message: String,
    val response: ProcessRiScResultDTO,
) : Exception()
