package no.risc.kubernetes.model

data class KubernetesSecret(
    val name: String,
    val secrets: List<KubernetesSecretData>
)

fun KubernetesSecret.toV1EnvVars() = secrets.map { it.toV1EnvVar(name) }
