package no.risc.crypto.sops

import no.risc.crypto.sops.utils.YamlInstance
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.collections.set

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

    fun decryptWithSopsConfig(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
        sopsAgeKey: String,
    ): RiScWithConfig {
        val sopsConfig = extractSopsConfig(ciphertext)
        val plaintext = decrypt(ciphertext, gcpAccessToken, sopsAgeKey)
        return RiScWithConfig(plaintext, sopsConfig)
    }

    fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
        sopsAgeKey: String,
    ): String {
        if (!SopsCryptoValidation.isValidGCPToken(gcpAccessToken.value)) {
            throw SOPSDecryptionException("Invalid GCP Token")
        }

        if (!SopsCryptoValidation.isValidAgeSecretKey(sopsAgeKey)) {
            throw SOPSDecryptionException("Invalid age key")
        }

        val processBuilder = ProcessBuilder().redirectErrorStream(true)

        val environment = processBuilder.environment()
        environment["SOPS_AGE_KEY"] = sopsAgeKey
        environment["GOOGLE_OAUTH_ACCESS_TOKEN"] = gcpAccessToken.value

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
                    0 -> { // Execution status OK
                        result
                    }

                    else -> {
                        val errorCode =
                            when {
                                result.contains("Failed to get the data key", ignoreCase = true) -> "MISSING_DATA_KEY"
                                result.contains("no key could decrypt", ignoreCase = true) -> "NO_MATCHING_KEY"
                                result.contains("authentication failed", ignoreCase = true) -> "AUTHENTICATION_FAILED"
                                result.contains("could not authenticate", ignoreCase = true) -> "AUTHENTICATION_FAILED"
                                else -> "DECRYPTION_FAILED"
                            }
                        throw SOPSDecryptionException(
                            message = result,
                            errorCode = errorCode,
                        )
                    }
                }
            }
    }
}
