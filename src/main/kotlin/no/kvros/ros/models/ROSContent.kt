package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ROSContentDTO(
    val content: String,
)
