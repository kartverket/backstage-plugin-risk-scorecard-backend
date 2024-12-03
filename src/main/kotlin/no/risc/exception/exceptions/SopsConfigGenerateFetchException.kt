package no.risc.exception.exceptions

import no.risc.risc.ProcessRiScResultDTO
import java.lang.Exception

data class SopsConfigGenerateFetchException(
    override val message: String,
    val response: ProcessRiScResultDTO,
) : Exception()
