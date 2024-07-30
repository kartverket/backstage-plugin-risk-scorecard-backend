package no.risc.encryption

import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.sensor
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.BufferedReader
import java.io.InputStreamReader

class SOPSDecryptionException(message: String) : Exception(message)

object SOPS : ISopsEncryption {
    private val logger: Logger = getLogger(SOPS::class.java)

    private val sopsCmd = listOf("sops")
    private val encrypt = listOf("encrypt")
    private val decrypt = listOf("decrypt")
    private val inputTypeYaml = listOf("--input-type", "yaml")
    private val inputTypeJson = listOf("--input-type", "json")
    private val outputTypeYaml = listOf("--output-type", "yaml")
    private val outputTypeJson = listOf("--output-type", "json")
    private val encryptConfig = listOf("--encrypt-config")
    private val inputFile = listOf("/dev/stdin")

    private val processBuilder = ProcessBuilder().redirectErrorStream(true)
    private const val EXECUTION_STATUS_OK = 0

    private fun toEncryptionCommand(
        config: String,
        accessToken: String,
    ): List<String> =
        sopsCmd + encrypt + inputTypeJson + outputTypeYaml + encryptConfig + config + inputFile +
            gcpAccessToken(
                accessToken,
            )

    private fun toDecryptionCommand(
        accessToken: String,
        sopsPrivateKey: String,
    ): List<String> =
        sopsCmd + ageSecret(sopsPrivateKey) + decrypt + inputTypeYaml + outputTypeJson + inputFile +
            gcpAccessToken(
                accessToken,
            )

    private fun gcpAccessToken(accessToken: String): List<String> = listOf("--gcp-access-token", accessToken)

    private fun ageSecret(sopsPrivateKey: String): List<String> = listOf("--age", sopsPrivateKey)

    override fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
        agePrivateKey: String,
    ): String {
        return processBuilder
            .command(toDecryptionCommand(gcpAccessToken.value, agePrivateKey))
            .start()
            .run {
                outputStream.buffered().also { it.write(ciphertext.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> result

                    else -> {
                        logger.error("IOException from decrypting yaml with error code ${exitValue()}: $result")
                        throw SOPSDecryptionException(result)
                    }
                }
            }
    }

    override fun encrypt(
        text: String,
        config: String,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String {
        return try {
            processBuilder
                .command(toEncryptionCommand(config, gcpAccessToken.value))
                .start()
                .run {
                    outputStream.buffered().also { it.write(text.toByteArray()) }.close()
                    val result = BufferedReader(InputStreamReader(inputStream)).readText()
                    when (waitFor()) {
                        EXECUTION_STATUS_OK -> result
                        else -> throw SopsEncryptionException(
                            message = "Failed when encrypting RiSc with ID: $riScId by running sops command: ${
                                toEncryptionCommand(
                                    config,
                                    gcpAccessToken.sensor().value,
                                )
                            } with error message: $result",
                            riScId = riScId,
                        )
                    }
                }
        } catch (e: Exception) {
            throw e
        }
    }
}
