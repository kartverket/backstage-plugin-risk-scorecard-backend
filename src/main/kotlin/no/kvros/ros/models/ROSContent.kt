package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileContentDTO(
    val content: String,
)
