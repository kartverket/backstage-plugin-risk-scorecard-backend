package no.risc.utils

import org.slf4j.Logger
import java.util.Base64
import java.util.stream.Stream
import kotlin.random.Random

data class Repository(
    val repositoryOwner: String,
    val repositoryName: String,
)

data class BackstageEntity(
    val kind: String,
    val namespace: String?,
    val name: String,
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
 * Generates a RiScId on the following format `<branchPrefix>-<5-letter-alphanumeric-string>`.
 */
fun generateRiScId(branchPrefix: String) = "$branchPrefix-${generateRandomAlphanumericString(5)}"

/**
 * Generates a RiScId based on Backstage entity info.
 *
 * - If only [riscName] is provided ([backstageEntity] is null), returns `<branchPrefix>-<riscName>`.
 * - If [backstageEntity] is present, returns
 *   `<branchPrefix>-<riscName>-backstage_<kind>_<namespace>_<name>`,
 *   where [BackstageEntity.namespace] defaults to `"default"` if not provided.
 * - Returns null if [riscName] is null.
 */
fun generateRiScIdFromBackstageInfo(
    branchPrefix: String,
    riscName: String?,
    backstageEntity: BackstageEntity?,
): String? {
    if (riscName == null) return null
    if (backstageEntity == null) return "$branchPrefix-$riscName"
    val namespace = backstageEntity.namespace ?: "default"
    return "$branchPrefix-$riscName-backstage_${backstageEntity.kind}_${namespace}_${backstageEntity.name}"
}

/**
 * Returns true if the given RiSc ID should be included when filtering by Backstage entity.
 *
 * - No filter ([backstageEntity] is null) → all IDs match.
 * - IDs without `-backstage_` segment → always match (old naming, backward compat).
 * - IDs with `-backstage_` segment → match only if they end with
 *   `-backstage_<kind>_<namespace>_<name>`,
 *   where [BackstageEntity.namespace] defaults to `"default"`.
 */
fun riScIdMatchesBackstageFilter(
    riScId: String,
    backstageEntity: BackstageEntity?,
): Boolean {
    if (backstageEntity == null) return true
    if (!riScId.contains("-backstage_")) return true
    val namespace = backstageEntity.namespace ?: "default"
    return riScId.endsWith("-backstage_${backstageEntity.kind}_${namespace}_${backstageEntity.name}")
}

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

/**
 * Returns the result of the provided function, unless the provided function throws an exception. If an exception is
 * thrown, then the error message is logged and the provided default value is returned.
 */
inline fun <T> tryOrDefaultWithErrorLogging(
    default: T,
    logger: Logger,
    func: () -> T,
): T =
    try {
        func()
    } catch (e: Exception) {
        logger.error(e.toString())
        default
    }

/**
 * Returns a [Result] containing the result of the provided function, or a failure if an exception is thrown.
 * If an exception is thrown, the error message is logged before returning the failure.
 */
inline fun <T> tryWithErrorLogging(
    logger: Logger,
    func: () -> T,
): Result<T> =
    try {
        Result.success(func())
    } catch (e: Exception) {
        logger.error(e.toString())
        Result.failure(e)
    }
