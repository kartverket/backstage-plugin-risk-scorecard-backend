package no.risc.kubernetes.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class SkipJobManifest(
    val apiVersion: String,
    val kind: String,
    val metadata: SkiperatorMetadata,
    val spec: SkipJobSpec,
) {
    fun toYamlString() =
        ObjectMapper(YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerKotlinModule()
            .writeValueAsString(this)
}

data class SkiperatorMetadata(
    val name: String,
    val namespace: String? = null,
    val annotations: SkiperatorMetadataAnnotations = SkiperatorMetadataAnnotations(),
)

data class SkiperatorMetadataAnnotations(
    @JsonProperty(
        "argocd.argoproj.io/tracking-id",
    ) val trackingId: String = "atgcp1-ros-plugin-main:skiperator.kartverket.no/SKIPJob:ros-plugin-main/",
)

data class SkipJobSpec(
    val container: SkiperatorContainerSpec,
    val job: SkiperatorJob,
)

data class SkiperatorJob(
    val ttlSecondsAfterFinished: Int,
    val backoffLimit: Int,
)

data class SkiperatorContainerSpec(
    val image: String,
    val env: List<SkiperatorContainerEnvEntry>? = null,
    val envFrom: List<ExternalSecretOperatorRef>? = null,
    val accessPolicy: SkiperatorContainerAccessPolicy? = null,
)

data class ExternalSecretOperatorRef(
    val secret: String,
)

data class SkiperatorContainerEnvEntry(
    val name: String,
    val value: String,
)

data class SkiperatorContainerAccessPolicy(
    val inbound: SkiperatorContainerInboundAccessPolicy? = null,
    val outbound: SkiperatorContainerOutboundAccessPolicy? = null,
)

data class SkiperatorContainerInboundAccessPolicy(
    val rules: List<SkiperatorContainerAccessPolicyRule>? = null,
)

data class SkiperatorContainerOutboundAccessPolicy(
    val external: List<SkiperatorContainerExternalAccessPolicyEntry>? = null,
    val rules: List<SkiperatorContainerAccessPolicyRule>? = null,
)

data class SkiperatorContainerExternalAccessPolicyEntry(
    val host: String,
)

data class SkiperatorContainerAccessPolicyRule(
    val application: String,
    val namespace: String,
)
