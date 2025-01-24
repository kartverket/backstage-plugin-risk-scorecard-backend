package no.risc.risc.models

data class RiScWrapperObject(
    val riSc: String,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
    val userInfo: UserInfo,
    val sopsConfig: String,
)
