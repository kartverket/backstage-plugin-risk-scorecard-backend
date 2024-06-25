package no.risc.risc.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserInfo(
    val name: String,
    val email: String,
)
