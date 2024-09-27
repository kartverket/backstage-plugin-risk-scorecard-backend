package no.risc.kubernetes

import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.models.*
import no.risc.config.KubernetesConfig
import no.risc.kubernetes.model.KubernetesEnvVar
import no.risc.kubernetes.model.KubernetesSecret
import no.risc.kubernetes.model.toV1EnvVars
import org.springframework.stereotype.Service

@Service
class KubernetesService(
    val kubernetesConfig: KubernetesConfig
) {

    fun applyKubernetesJob(
        kubernetesJobName: String,
        imageUrl: String,
        kubernetesSecret: KubernetesSecret? = null,
        envVars: List<KubernetesEnvVar> = emptyList()
    ) {
        if (kubernetesSecret != null) {
            applyKubernetesSecret(kubernetesSecret)
        }

        val job = V1Job()
            .metadata(
                V1ObjectMeta()
                    .name(kubernetesJobName)
            )
            .spec(
                V1JobSpec()
                    .template(
                        V1PodTemplateSpec()
                            .spec(
                                V1PodSpec()
                                    .containers(
                                        listOf(
                                            V1Container()
                                                .image(imageUrl)
                                                .env(
                                                    (kubernetesSecret?.toV1EnvVars() ?: emptyList<V1EnvVar>()) + envVars.toV1EnvVars()
                                                )
                                        )
                                    )
                            )
                    )

            )
        try {
            kubernetesConfig.batchV1Api()
                .createNamespacedJob(kubernetesConfig.namespace, job)
        } catch (e: ApiException) {
            throw e
        }
    }

    private fun applyKubernetesSecret(
        kubernetesSecret: KubernetesSecret
    ) {
        val secret = V1Secret()
            .metadata(
                V1ObjectMeta()
                    .name(kubernetesSecret.name)
            )
            .type("Opaque")
            .data(
                kubernetesSecret.secrets.associate {
                    it.key to it.data
                }
            )
        try {
            kubernetesConfig.coreV1Api()
                .createNamespacedSecret(kubernetesConfig.namespace, secret)
        } catch (e: ApiException) {
            throw e
        }
    }
}
