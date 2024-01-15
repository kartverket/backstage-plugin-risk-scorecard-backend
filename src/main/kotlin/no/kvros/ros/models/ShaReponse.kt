package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class ShaResponseDTO(
    val name: String,
    val path: String,
    val sha: String,
)
