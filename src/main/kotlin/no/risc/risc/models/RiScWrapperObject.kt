package no.risc.risc.models

data class RiScWrapperObject(
    val riSc: String,
    val sopsConfig: String? = null,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
    val userInfo: UserInfo,
)
