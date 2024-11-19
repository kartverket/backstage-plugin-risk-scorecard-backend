@file:Suppress("ktlint:standard:filename")

package no.risc.sops.model

import no.risc.risc.ProcessingStatus

data class GetSopsConfigResponse(
    val status: ProcessingStatus,
    val gcpProjectId: GcpProjectId,
    val gcpProjectIds: List<GcpProjectId>,
    val publicAgeKeys: List<PublicAgeKey>,
)
