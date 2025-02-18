package no.risc.risc.models

import no.risc.sops.model.SopsConfig

data class RiScWrapperObject(
    val riSc: String,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
    val userInfo: UserInfo,
    val sopsConfig: SopsConfig,
)
