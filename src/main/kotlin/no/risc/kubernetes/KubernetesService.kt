package no.risc.kubernetes

import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.util.Yaml
import no.risc.config.SkiperatorConfig
import no.risc.kubernetes.model.*
import org.springframework.stereotype.Service


@Service
class KubernetesService(
    private val skiperatorConfig: SkiperatorConfig
) {

    fun applySkipJob(
        name: String,
        namespace: String,
        imageUrl: String,
        envVars: List<SkiperatorContainerEnvEntry>? = null,
        externalSecretsName: String? = null,
        accessPolicy: SkiperatorContainerAccessPolicy? = null,
        skiperatorJob: SkiperatorJob = SkiperatorJob(
            ttlSecondsAfterFinished = 1,
            backoffLimit = 1
        )
    ) {
        val skipJob = SkipJobManifest(
            apiVersion = "skiperator.kartverket.no/v1alpha1",
            kind = "SKIPJob",
            metadata = SkiperatorMetadata(
                name = name,
                namespace = namespace,
            ),
            spec = SkipJobSpec(
                container = SkiperatorContainerSpec(
                    image = imageUrl,
                    env = envVars,
                    envFrom = listOf(
                        ExternalSecretOperatorRef(
                            secret = externalSecretsName ?: throw IllegalStateException("Cannot inject secrets without specifying ref. to External Secrets object")
                        )
                    ),
                    accessPolicy = accessPolicy
                ),
                job = skiperatorJob
            )
        )
        try {
            skiperatorConfig.customObjectsApi()
                .createNamespacedCustomObject(
                    "skiperator.kartverket.no",
                    "v1alpha1",
                    namespace,
                    "skipjobs",
                    Yaml.load(skipJob.toYamlString())
                )
        } catch (e: ApiException) {
            throw e
        }
    }
}
