package no.risc.exception.exceptions

import no.risc.risc.ProcessRiScResultDTO

data class GcpProjectIdFetchException(
    override val message: String,
    val response: ProcessRiScResultDTO,
) : Exception()
