package no.risc.crypto.sops

import no.risc.crypto.sops.utils.YamlInstance
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.risc.models.SopsConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@ConfigurationProperties(prefix = "crypto.sops")
@Service
class SopsCryptoService {
    lateinit var backendPublicKey: String
    lateinit var securityTeamPublicKey: String
    lateinit var securityPlatformPublicKey: String

    fun extractSopsConfig(ciphertext: String): SopsConfig {
        val rootNode = YamlInstance.objectMapper.readTree(ciphertext)
        val sopsNode =
            rootNode.get("sops")
                ?: throw SOPSDecryptionException(
                    "No sops configuration found in ciphertext",
                )
        val sopsConfig =
            YamlInstance.objectMapper.treeToValue(sopsNode, SopsConfig::class.java)
        val cleanConfig =
            sopsConfig.copy(
                gcpKms =
                    sopsConfig.keyGroups?.flatMap { it.gcpKms ?: emptyList() },
                age =
                    sopsConfig
                        .keyGroups
                        ?.flatMap { it.age ?: emptyList() }
                        ?.filter {
                            it.recipient != securityTeamPublicKey
                        }?.filter { it.recipient != backendPublicKey }
                        ?.filter {
                            it.recipient != securityPlatformPublicKey
                        },
                shamirThreshold = sopsConfig.shamirThreshold,
                lastModified = sopsConfig.lastModified,
                version = sopsConfig.version,
                keyGroups = emptyList(),
            )
        return cleanConfig
    }
}
