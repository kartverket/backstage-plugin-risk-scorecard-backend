package no.risc.exception.exceptions

import java.lang.Exception

data class JSONSchemaFetchException(
    override val message: String,
    val riScId: String,
) : Exception()
