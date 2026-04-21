package no.risc.crypto.sops

import no.risc.crypto.sops.model.RiScWithConfig
import no.risc.crypto.sops.model.SopsConfig
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.models.GCPAccessToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.collections.set

@Service
class SopsCryptoService(
    private val props: SopsCryptoProperties,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(SopsCryptoService::class.java)

        const val EXECUTION_STATUS_OK_CODE = 0
    }

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
                gcp_kms =
                    sopsConfig.key_groups?.flatMap { it.gcp_kms ?: emptyList() },
                age =
                    sopsConfig
                        .key_groups
                        ?.flatMap { it.age ?: emptyList() }
                        ?.filter {
                            it.recipient != props.securityTeamPublicKey
                        }?.filter { it.recipient != props.backendPublicKey }
                        ?.filter {
                            it.recipient != props.securityPlatformPublicKey
                        },
                shamir_threshold = sopsConfig.shamir_threshold,
                lastmodified = sopsConfig.lastmodified,
                version = sopsConfig.version,
                key_groups = emptyList(),
            )
        return cleanConfig
    }

    fun decryptWithSopsConfig(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig {
        val sopsConfig = extractSopsConfig(ciphertext)
        val plaintext =
            try {
                decrypt(ciphertext, gcpAccessToken, props.agePrivateKey)
            } catch (e: SOPSDecryptionException) {
                val keyId =
                    sopsConfig.gcp_kms?.firstOrNull()?.resource_id
                        ?: sopsConfig.age?.firstOrNull()?.recipient
                throw SOPSDecryptionException(
                    message = e.message,
                    errorCode = e.errorCode,
                    errorMessage = e.errorMessage,
                    encryptionKeyId = keyId,
                )
            }
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
                    EXECUTION_STATUS_OK_CODE -> {
                        result
                    }

                    else -> {
                        val errorCode =
                            when {
                                result.contains("Failed to get the data key", ignoreCase = true) -> "MISSING_DATA_KEY"
                                result.contains("no key could decrypt", ignoreCase = true) -> "NO_MATCHING_KEY"
                                result.contains("authentication failed", ignoreCase = true) -> "AUTHENTICATION_FAILED"
                                result.contains("could not authenticate", ignoreCase = true) -> "AUTHENTICATION_FAILED"
                                else -> "" // TODO: Improve the error handling for the else case
                            }
                        throw SOPSDecryptionException(
                            message = result,
                            errorCode = errorCode,
                            errorMessage = SOPSDecryptionException.errorMessageFromCode(errorCode),
                        )
                    }
                }
            }
    }

    fun encrypt(
        text: String,
        config: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String {
        if (!SopsCryptoValidation.isValidGCPToken(gcpAccessToken.value)) {
            throw SopsEncryptionException("Invalid GCP Token", riScId)
        }

        // Create key groups configuration
        val keyGroups =
            listOfNotNull(
                mapOf(
                    "age" to listOf(props.securityTeamPublicKey),
                    "gcp_kms" to
                        listOf(
                            mapOf(
                                "resource_id" to
                                    config.gcp_kms
                                        ?.first()
                                        ?.resource_id,
                            ),
                        ),
                ),
                mapOf(
                    "age" to
                        listOf(props.backendPublicKey, props.securityPlatformPublicKey),
                ),
                config.age?.let { ageKeys ->
                    val developerKeys =
                        ageKeys.map { it.recipient }.filter {
                            it !in
                                setOf(
                                    props.securityTeamPublicKey,
                                    props.backendPublicKey,
                                    props.securityPlatformPublicKey,
                                )
                        }
                    if (developerKeys.isNotEmpty()) {
                        mapOf("age" to developerKeys)
                    } else {
                        null
                    }
                },
            )

        // Create SOPS config
        val sopsConfig =
            mapOf(
                "creation_rules" to
                    listOf(
                        mapOf(
                            "shamir_threshold" to
                                config.shamir_threshold,
                            "key_groups" to keyGroups,
                        ),
                    ),
            )

        // Create temporary config file
        val prefix = randomBech32("sopsConfig-", 6) + System.currentTimeMillis()
        val tempConfigFile = File.createTempFile(prefix, ".yaml")
        tempConfigFile.writeText(YamlInstance.objectMapper.writeValueAsString(sopsConfig))
        tempConfigFile.deleteOnExit()

        // Set the access token in the environment rather than interpolating it into a shell command
        val processBuilder = ProcessBuilder().redirectErrorStream(true)
        val environment = processBuilder.environment()
        environment["GOOGLE_OAUTH_ACCESS_TOKEN"] = gcpAccessToken.value

        return processBuilder
            .command(
                "sops",
                "--encrypt",
                "--input-type",
                "json",
                "--output-type",
                "yaml",
                "--config",
                tempConfigFile.absolutePath,
                "/dev/stdin",
            ).start()
            .run {
                outputStream.buffered().also { it.write(text.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK_CODE -> {
                        tempConfigFile.delete()
                        result
                    }

                    else -> {
                        tempConfigFile.delete()
                        LOGGER.error(
                            "SOPS encryption failed with exit code ${exitValue()}: $result",
                        )
                        throw SopsEncryptionException(
                            message =
                                "Failed when encrypting RiSc with ID: $riScId ",
                            riScId = riScId,
                        )
                    }
                }
            }
    }
}
