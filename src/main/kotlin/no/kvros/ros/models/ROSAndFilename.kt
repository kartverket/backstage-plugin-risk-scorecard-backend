package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ROSAndFilename(
    @JsonProperty("content")
    val ros: String,
    @JsonProperty("name")
    val filename: String
)
