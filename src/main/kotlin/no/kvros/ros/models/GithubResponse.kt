package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileContentDTO(
    @JsonProperty("content")
    val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShaResponseDTO(
    @JsonProperty("sha")
    val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileNameDTO(
    val name: String,
)
