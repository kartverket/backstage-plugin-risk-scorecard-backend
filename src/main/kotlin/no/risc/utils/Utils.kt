package no.risc.utils

import java.util.Base64
import java.util.stream.Stream
import kotlin.random.Random

data class Repository(
    val repositoryOwner: String,
    val repositoryName: String,
)

fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())

fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(toByteArray()).decodeToString()

fun generateRiScId(filenamePrefix: String) = "$filenamePrefix-${generateRandomAlphanumericString(5)}"

fun generateSopsId() = "sops-${generateRandomAlphanumericString(5)}"

private val alphaNumericChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

/**
 * Generates a random alphanumeric string
 *
 * @param length The number of characters in the generated string
 * @param random The random number generator to use, defaults to `Random.Default` if not supplied.
 */
fun generateRandomAlphanumericString(
    length: Int,
    random: Random = Random,
): String =
    Stream
        .generate { alphaNumericChars.random(random) }
        .limit(length.toLong())
        .toArray()
        .joinToString(separator = "")
