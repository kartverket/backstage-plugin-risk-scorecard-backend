package no.risc.exception.exceptions

import java.lang.Exception

data class JSONSchemaFetchException(
    override val message: String,
    val onUpdateOfRiSC: Boolean,
    val riScId: String,
) : Exception()
