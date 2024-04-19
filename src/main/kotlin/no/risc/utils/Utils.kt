package no.risc.utils

import no.risc.risc.models.FileNameDTO

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
