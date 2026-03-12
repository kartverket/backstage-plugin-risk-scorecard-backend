package no.risc.crypto.sops

import no.risc.crypto.sops.utils.YamlInstance
import no.risc.crypto.sops.utils.randomBech32
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.collections.set

@ConfigurationProperties(prefix = "crypto.sops")
@Service
class SopsCryptoService {
    lateinit var backendPublicKey: String
    lateinit var securityTeamPublicKey: String
    lateinit var securityPlatformPublicKey: String

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(SopsCryptoService::class.java)
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
                    "age" to listOf(securityTeamPublicKey),
                    "gcp_kms" to
                        listOf(
                            mapOf(
                                "resource_id" to
                                    config.gcpKms
                                        ?.first()
                                        ?.resourceId,
                            ),
                        ),
                ),
                mapOf(
                    "age" to
                        listOf(backendPublicKey, securityPlatformPublicKey),
                ),
                config.age?.let { ageKeys ->
                    val developerKeys =
                        ageKeys.map { it.recipient }.filter {
                            it !in
                                setOf(
                                    securityTeamPublicKey,
                                    backendPublicKey,
                                    securityPlatformPublicKey,
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
                                config.shamirThreshold,
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
                    0 -> { // Execution status = OK
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
