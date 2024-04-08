package no.kvros.ros.models

data class ROSWrapperObject(
    val ros: String,
    val isRequiresNewApproval: Boolean,
    val schemaVersion: String,
)
