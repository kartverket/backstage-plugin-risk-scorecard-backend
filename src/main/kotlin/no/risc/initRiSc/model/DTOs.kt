package no.risc.initRiSc.model

import no.risc.sops.model.GcpCryptoKeyObject
import no.risc.sops.model.PublicAgeKey

data class GenerateRiScRequestBody(
    val initialRiSc: String,
)

data class GenerateSopsConfigRequestBody(
    val gcpCryptoKey: GcpCryptoKeyObject,
    val publicAgeKeys: List<PublicAgeKey>,
)
