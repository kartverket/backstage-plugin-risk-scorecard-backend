package no.risc.utils

import no.risc.github.models.FileNameDTO
import java.util.Base64

fun getFileNameWithHighestVersion(files: List<FileNameDTO>): String? {
    return files.maxByOrNull { dto ->
        val version = dto.value.substringAfterLast("_v").substringBefore(".json")
        val (major, minor) = version.split("_").map { it.toInt() }
        MajorMinorVersion(major, minor)
    }?.value
}

data class MajorMinorVersion(val major: Int, val minor: Int) : Comparable<MajorMinorVersion> {
    override fun compareTo(other: MajorMinorVersion): Int {
        return if (major != other.major) {
            major.compareTo(other.major)
        } else {
            minor.compareTo(other.minor)
        }
    }
}

fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())

fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(toByteArray()).decodeToString()


fun removePathRegex(_config: String, riScId: String): String {
    val regex = "(?<pathregex>path_regex:.*)".toRegex()

    try {
        val matchResult = regex.find(_config)!!
        var config = _config

        // On match with pathregex, remove the parameter from the config. The backend is not working with a filesystem.
        if (matchResult.groups["pathregex"] != null) {
            config = _config.replace(matchResult.groups["pathregex"]!!.value, "")
        }

        return config

    }
    catch (e: Exception) {
        throw e
    }
}