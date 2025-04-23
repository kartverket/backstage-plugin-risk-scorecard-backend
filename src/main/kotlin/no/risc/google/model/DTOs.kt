@file:Suppress("ktlint:standard:no-empty-file")

package no.risc.google.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FetchGcpProjectIdsResponse(
    val projects: List<GcpProject>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GcpProject(
    val projectId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TestIamPermissionBody(
    val permissions: List<GcpIamPermission>? = null,
)
