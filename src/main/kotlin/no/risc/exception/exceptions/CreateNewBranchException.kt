package no.risc.exception.exceptions

import no.risc.risc.ProcessRiScResultDTO

data class CreateNewBranchException(
    override val message: String,
    val response: ProcessRiScResultDTO,
) : Exception()
