package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class ContentResponseDTO(
    val name: String,
    val path: String,
    val sha: ShaResponseDTO,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShaResponseDTO(
    @JsonProperty("sha")
    val value: String
)
