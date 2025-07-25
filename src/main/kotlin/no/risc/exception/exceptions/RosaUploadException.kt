package no.risc.exception.exceptions

data class RosaUploadException(
    override val message: String,
    val riScId: String,
) : Exception()
