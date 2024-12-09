package no.risc.exception.exceptions

import no.risc.sops.model.GetSopsConfigResponseBody

data class NoSopsConfigFoundException(
    override val message: String,
    val response: GetSopsConfigResponseBody,
) : Exception()
