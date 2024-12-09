package no.risc.initRiSc.model

import no.risc.sops.model.GcpProjectId
import no.risc.sops.model.PublicAgeKey

data class GenerateRiScRequestBody(
    val initialRiSc: String,
)

data class GenerateSopsConfigRequestBody(
    val gcpProjectId: GcpProjectId,
    val publicAgeKeys: List<PublicAgeKey>,
)
