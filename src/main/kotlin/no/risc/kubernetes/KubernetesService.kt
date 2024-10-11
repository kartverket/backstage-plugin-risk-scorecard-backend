package no.risc.kubernetes

import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.util.Yaml
import no.risc.config.SkiperatorConfig
import no.risc.kubernetes.model.ExternalSecretOperatorRef
import no.risc.kubernetes.model.SkipJobManifest
import no.risc.kubernetes.model.SkipJobSpec
import no.risc.kubernetes.model.SkiperatorContainerAccessPolicy
import no.risc.kubernetes.model.SkiperatorContainerEnvEntry
import no.risc.kubernetes.model.SkiperatorContainerSpec
import no.risc.kubernetes.model.SkiperatorJob
import no.risc.kubernetes.model.SkiperatorMetadata
import org.springframework.stereotype.Service

@Service
class KubernetesService(
    private val skiperatorConfig: SkiperatorConfig,
) {
    fun applySkipJob(
        name: String,
        namespace: String,
        imageUrl: String,
        envVars: List<SkiperatorContainerEnvEntry>? = null,
        externalSecretsName: String? = null,
        accessPolicy: SkiperatorContainerAccessPolicy? = null,
        skiperatorJob: SkiperatorJob =
            SkiperatorJob(
                ttlSecondsAfterFinished = 1,
                backoffLimit = 1,
            ),
    ) {
        val skipJob =
            SkipJobManifest(
                apiVersion = "skiperator.kartverket.no/v1alpha1",
                kind = "SKIPJob",
                metadata =
                    SkiperatorMetadata(
                        name = name,
                        namespace = namespace,
                    ),
                spec =
                    SkipJobSpec(
                        container =
                            SkiperatorContainerSpec(
                                image = imageUrl,
                                env = envVars,
                                envFrom =
                                    externalSecretsName?.let {
                                        listOf(
                                            ExternalSecretOperatorRef(
                                                secret = it,
                                            ),
                                        )
                                    },
                                accessPolicy = accessPolicy,
                            ),
                        job = skiperatorJob,
                    ),
            )
        try {
            skiperatorConfig.customObjectsApi()
                .createNamespacedCustomObject(
                    "skiperator.kartverket.no",
                    "v1alpha1",
                    namespace,
                    "skipjobs",
                    Yaml.load(skipJob.toYamlString()),
                )
        } catch (e: ApiException) {
            throw e
        }
    }
}
