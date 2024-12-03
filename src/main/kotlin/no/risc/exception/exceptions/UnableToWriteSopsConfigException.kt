package no.risc.exception.exceptions

import no.risc.risc.ProcessRiScResultDTO

data class UnableToWriteSopsConfigException(
    override val message: String,
    val response: ProcessRiScResultDTO,
) : Exception()
