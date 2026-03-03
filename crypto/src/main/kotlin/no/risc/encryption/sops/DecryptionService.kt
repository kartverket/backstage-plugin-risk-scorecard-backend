package no.risc.encryption.sops

import no.risc.encryption.sops.validation.CryptoValidation
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader

@ConfigurationProperties(prefix = "sops.decryption")
@Service
class DecryptionService {
    private val processBuilder = ProcessBuilder().redirectErrorStream(true)
    lateinit var backendPublicKey: String
    lateinit var securityTeamPublicKey: String
    lateinit var securityPlatformPublicKey: String

    fun extractSopsConfig(ciphertext: String): SopsConfig {
        val rootNode = YamlUtils.objectMapper.readTree(ciphertext)
        val sopsNode =
            rootNode.get("sops")
                ?: throw SOPSDecryptionException(
                    "No sops configuration found in ciphertext",
                )
        val sopsConfig =
            YamlUtils.objectMapper.treeToValue(sopsNode, SopsConfig::class.java)
        val cleanConfig =
            sopsConfig.copy(
                gcpKms =
                    sopsConfig.keyGroups?.flatMap { it.gcpKms ?: emptyList() } ?: emptyList(),
                age =
                    sopsConfig.keyGroups
                        ?.flatMap { it.age ?: emptyList() }
                        ?.filter { it.recipient != securityTeamPublicKey }
                        ?.filter { it.recipient != backendPublicKey }
                        ?.filter { it.recipient != securityPlatformPublicKey },
                shamirThreshold = sopsConfig.shamirThreshold,
                lastModified = sopsConfig.lastModified,
                version = sopsConfig.version,
                keyGroups = emptyList(),
            )
        return cleanConfig
    }

    fun decryptWithSopsConfig(
        ciphertext: String,
        gcpAccessToken: String,
        sopsAgeKey: String,
    ): RiScWithConfig {
        val sopsConfig = extractSopsConfig(ciphertext)
        val plaintext = decrypt(ciphertext, gcpAccessToken, sopsAgeKey)
        return RiScWithConfig(plaintext, sopsConfig)
    }

    fun decrypt(
        ciphertext: String,
        gcpAccessToken: String,
        sopsAgeKey: String,
    ): String {
        if (!CryptoValidation.isValidGCPToken(gcpAccessToken)) {
            throw SOPSDecryptionException("Invalid GCP Token")
        }

        if (!CryptoValidation.isValidAgeSecretKey(sopsAgeKey)) {
            throw SOPSDecryptionException("Invalid age key")
        }

        val environment = processBuilder.environment()
        environment["SOPS_AGE_KEY"] = sopsAgeKey
        environment["GOOGLE_OAUTH_ACCESS_TOKEN"] = gcpAccessToken

        return processBuilder
            .command(
                listOf(
                    "sops",
                    "decrypt",
                    "--input-type",
                    "yaml",
                    "--output-type",
                    "json",
                    "/dev/stdin",
                ),
            ).start()
            .run {
                outputStream
                    .buffered()
                    .also { it.write(ciphertext.toByteArray()) }
                    .close()
                val result =
                    BufferedReader(InputStreamReader(inputStream))
                        .readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> result
                    else -> {
                        throw SOPSDecryptionException(
                            message = "Decrypting message failed with error: $result",
                        )
                    }
                }
            }
    }
}
