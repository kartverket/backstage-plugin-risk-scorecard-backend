package no.risc.exception.exceptions

import no.risc.sops.model.GetSopsConfigResponse

data class GcpProjectIdFetchException(
    override val message: String,
    val response: GetSopsConfigResponse,
) : Exception()
