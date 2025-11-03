package no.risc.risc.models

import kotlinx.serialization.Serializable

enum class DefaultRiScType {
    Ops,
    InternalJob,
    Standard,
}

@Serializable
data class NewRiScRequestBody(
    val riSc: String,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
    val userInfo: UserInfo,
    val sopsConfig: SopsConfig,
    val defaultRiScId: String,
) {
    fun toRiScWrapperObject(): RiScWrapperObject =
        RiScWrapperObject(
            riSc,
            isRequiresNewApproval,
            schemaVersion,
            userInfo,
            sopsConfig,
        )
}
