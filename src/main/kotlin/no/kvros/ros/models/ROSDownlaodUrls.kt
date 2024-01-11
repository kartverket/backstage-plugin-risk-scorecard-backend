package no.kvros.ros.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ROSDownloadUrl(
    val download_url: String,
)