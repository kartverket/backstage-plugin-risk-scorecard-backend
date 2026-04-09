package no.risc.crypto.sops

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "crypto.sops")
data class SopsCryptoProperties(
    val backendPublicKey: String,
    val securityTeamPublicKey: String,
    val securityPlatformPublicKey: String,
    val agePrivateKey: String,
)
