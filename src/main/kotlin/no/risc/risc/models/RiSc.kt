package no.risc.risc.models

import no.risc.infra.connector.UserInfo

data class RiScWrapperObject(
    val riSc: String,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
    val userInfo: UserInfo,
)
