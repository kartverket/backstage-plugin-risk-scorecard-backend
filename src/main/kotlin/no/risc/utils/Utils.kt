package no.risc.utils

import java.util.Base64
import java.util.stream.Stream
import kotlin.random.Random

data class Repository(
    val repositoryOwner: String,
    val repositoryName: String,
)

/**
 * Encodes the string as Base64.
 */
fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())

/**
 * Decodes the string from Base64.
 */
fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(toByteArray()).decodeToString()

/**
 * Generates a RiScId on the following format `<filenamePrefix>-<5-letter-alphanumeric-string>`.
 */
fun generateRiScId(filenamePrefix: String) = "$filenamePrefix-${generateRandomAlphanumericString(5)}"

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

/**
 * Returns the result of the provided function, unless the provided function throws an exception. If an exception is
 * thrown, then null is returned.
 */
inline fun <T> tryOrNull(func: () -> T?): T? = tryOrDefault(default = null, func = func)

/**
 * Returns the result of the provided function, unless the provided function throws an exception. If an exception is
 * thrown, then the provided default value is returned.
 */
inline fun <T> tryOrDefault(
    default: T,
    func: () -> T,
): T =
    try {
        func()
    } catch (_: Exception) {
        default
    }
