package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ROSDownloadUrls(
    val download_url: String,
)
