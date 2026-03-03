package no.risc.encryption.sops

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.risc.encryption.sops.validation.CryptoValidation
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.risc.models.SopsConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@ConfigurationProperties(prefix = "sops.encryption")
@Service
class EncryptionService {
    private val processBuilder = ProcessBuilder().redirectErrorStream(true)
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    private val logger = LoggerFactory.getLogger(EncryptionService::class.java)
    lateinit var backendPublicKey: String
    lateinit var securityTeamPublicKey: String
    lateinit var securityPlatformPublicKey: String

    fun encrypt(
        text: String,
        config: SopsConfig,
        gcpAccessToken: String,
        riScId: String,
    ): String {
        if (!CryptoValidation.isValidGCPToken(gcpAccessToken)) {
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
                                        .first()
                                        .resourceId,
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
                            "shamir_threshold" to config.shamirThreshold,
                            "key_groups" to keyGroups,
                        ),
                    ),
            )

        // Create temporary config file
        val prefix = randomBech32("sopsConfig-", 6) + System.currentTimeMillis()
        val tempConfigFile = File.createTempFile(prefix, ".yaml")
        tempConfigFile.writeText(yamlMapper.writeValueAsString(sopsConfig))
        tempConfigFile.deleteOnExit()

        // Set the access token in the environment rather than interpolating it into a shell command
        val environment = processBuilder.environment()
        environment["GOOGLE_OAUTH_ACCESS_TOKEN"] = gcpAccessToken

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
                    EXECUTION_STATUS_OK -> {
                        tempConfigFile.delete()
                        result
                    }
                    else -> {
                        tempConfigFile.delete()
                        logger.error(
                            "SOPS encryption failed with exit code ${exitValue()}: $result",
                        )
                        throw SopsEncryptionException(
                            message = "Failed when encrypting RiSc with ID: $riScId ",
                            riScId = riScId,
                        )
                    }
                }
            }
    }

    companion object {
        private const val EXECUTION_STATUS_OK = 0
    }
}
