package no.risc.github.models

data class SopsConfigOnGitHub(
    val config: String,
    val commitSha: String? = null,
)
