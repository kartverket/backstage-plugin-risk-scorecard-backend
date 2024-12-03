package no.risc.infra.connector.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FetchGcpProjectIdsResponse(
    val projects: List<GcpProject>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GcpProject(
    val projectId: String,
)
