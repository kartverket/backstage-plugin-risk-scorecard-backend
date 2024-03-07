package no.kvros.encryption

import no.kvros.infra.connector.models.GCPAccessToken
import java.io.BufferedReader
import java.io.InputStreamReader

class SOPSDecryptionException(message: String) : Exception(message)

class SOPSEncryptionException(message: String) : Exception(message)

object SOPS {
    private val sopsCmd = listOf("./our-sops") // TODO: Sett riktig path til sops
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

    private fun toEncryptionCommand(config: String): List<String> =
        sopsCmd + encrypt + inputTypeJson + outputTypeYaml + encryptConfig + config + inputFile

    private fun toDecryptionCommand(accessToken: String): List<String> =
        sopsCmd + decrypt + inputTypeYaml + outputTypeJson + gcpAccessToken(accessToken) + inputFile

    private fun gcpAccessToken(accessToken: String): List<String> = listOf("--gcp-access-token", accessToken)

    fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): String =
        processBuilder
            .command(toDecryptionCommand(gcpAccessToken.value))
            .start()
            .run {
                outputStream.buffered().also { it.write(ciphertext.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> result

                    else ->
                        throw SOPSDecryptionException("IOException from decrypting yaml with error code ${exitValue()}: $result")
                }
            }

    fun encrypt(
        text: String,
        config: String,
    ): String =
        processBuilder
            .command(toEncryptionCommand(config))
            .start()
            .run {
                outputStream.buffered().also { it.write(text.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> result

                    else -> throw SOPSEncryptionException("IOException from encrypting json with error code ${exitValue()}: $result")
                }
            }
}
