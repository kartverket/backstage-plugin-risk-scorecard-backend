package no.risc.initRiSc.model

import no.risc.risc.models.UserInfo

data class GenerateRiScResponseBody(
    val sopsConfig: String,
    val schemaVersion: String,
    val initialRiScContent: String,
    val userInfo: UserInfo,
)

data class GenerateRiScRequestBody(
    val publicAgeKey: String? = null,
    val gcpProjectId: String,
)
