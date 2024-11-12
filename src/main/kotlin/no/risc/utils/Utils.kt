package no.risc.utils

import no.risc.github.models.FileNameDTO
import org.apache.commons.lang3.RandomStringUtils
import java.util.Base64

fun getFileNameWithHighestVersion(files: List<FileNameDTO>): String? =
    files
        .maxByOrNull { dto ->
            val version = dto.value.substringAfterLast("_v").substringBefore(".json")
            val (major, minor) = version.split("_").map { it.toInt() }
            MajorMinorVersion(major, minor)
        }?.value

data class MajorMinorVersion(
    val major: Int,
    val minor: Int,
) : Comparable<MajorMinorVersion> {
    override fun compareTo(other: MajorMinorVersion): Int =
        if (major != other.major) {
            major.compareTo(other.major)
        } else {
            minor.compareTo(other.minor)
        }
}

data class Repository(
    val repositoryOwner: String,
    val repositoryName: String,
)

fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())

fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(toByteArray()).decodeToString()

fun generateRiScId(filenamePrefix: String) = "$filenamePrefix-${RandomStringUtils.randomAlphanumeric(5)}"

fun removePathRegex(config: String): String {
    val regex = "(?<pathregex>path_regex:.*)".toRegex()

    val matchResult = regex.find(config)

    // On match with pathregex, remove the parameter from the config. The backend is not working with a filesystem.
    return if (matchResult?.groups?.get("pathregex") != null) {
        config.replace(matchResult.groups["pathregex"]!!.value, "")
    } else {
        config
    }
}

fun modifySopsConfigForGitHub(config: String) =
    config
        .lines()
        .drop(1) // Remove the first line (---)
        .joinToString("\n")
        .replace("\"\\\\.risc\\\\.yaml$\"", "\\.risc\\.yaml$")
