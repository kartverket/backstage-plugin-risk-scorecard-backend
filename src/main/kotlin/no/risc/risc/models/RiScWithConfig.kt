package no.risc.risc.models

import no.risc.sops.model.SopsConfig

data class RiScWithConfig(
    val riSc: String,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
    val userInfo: UserInfo? = null,
    val sopsConfig: SopsConfig,
)
