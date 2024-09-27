package no.risc.kubernetes.model

import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1EnvVarSource
import io.kubernetes.client.openapi.models.V1SecretKeySelector

data class KubernetesSecretData(
    val key: String,
    val data: ByteArray
) {
    fun toV1EnvVar(
        kubernetesSecretName: String
    ) = V1EnvVar()
        .name(key)
        .valueFrom(
            V1EnvVarSource()
                .secretKeyRef(
                    V1SecretKeySelector()
                        .name(kubernetesSecretName)
                        .key(key)
                )
        )
}
