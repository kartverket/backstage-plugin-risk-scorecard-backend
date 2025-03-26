package no.risc.utils

import org.apache.commons.lang3.RandomStringUtils
import java.util.Base64

data class Repository(
    val repositoryOwner: String,
    val repositoryName: String,
)

fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())

fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(toByteArray()).decodeToString()

fun generateRiScId(filenamePrefix: String) = "$filenamePrefix-${RandomStringUtils.randomAlphanumeric(5)}"

fun generateSopsId() = "sops-${RandomStringUtils.randomAlphanumeric(5)}"
