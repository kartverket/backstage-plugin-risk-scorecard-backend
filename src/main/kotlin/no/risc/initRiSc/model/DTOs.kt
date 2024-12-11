package no.risc.initRiSc.model

import no.risc.sops.model.PublicAgeKey

data class GenerateRiScRequestBody(
    val initialRiSc: String,
)

data class GenerateSopsConfigRequestBody(
    val gcpCryptoKey: GenerateSopsConfigGcpCryptoKeyObject,
    val publicAgeKeys: List<PublicAgeKey>,
)

data class GenerateSopsConfigGcpCryptoKeyObject(
    val projectId: String,
    val keyRing: String,
    val name: String,
)
