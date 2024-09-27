package no.risc.kubernetes.model

import io.kubernetes.client.openapi.models.V1EnvVar

data class KubernetesEnvVar(
    val name: String,
    val value: String
) {
    fun toV1EnvVar() = V1EnvVar()
        .name(name)
        .value(value)
}

fun List<KubernetesEnvVar>.toV1EnvVars() = map { it.toV1EnvVar() }
