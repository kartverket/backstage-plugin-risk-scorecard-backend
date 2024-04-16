package no.risc.encryption

import java.io.BufferedReader
import java.io.InputStreamReader
import no.risc.infra.connector.models.GCPAccessToken

class SOPSDecryptionException(message: String) : Exception(message)

class SOPSEncryptionException(message: String) : Exception(message)

object SOPS {

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
        sopsCmd + gcpAccessToken(accessToken) + encrypt + inputTypeJson + outputTypeYaml + encryptConfig + config + inputFile

    private fun toDecryptionCommand(accessToken: String, sopsPrivateKey: String): List<String> =
        sopsCmd + gcpAccessToken(accessToken) + ageSecret(
            sopsPrivateKey
        ) + decrypt + inputTypeYaml + outputTypeJson + inputFile

    private fun gcpAccessToken(accessToken: String): List<String> = listOf("--gcp-access-token", accessToken)
    private fun ageSecret(sopsPrivateKey: String): List<String> = listOf("--age", sopsPrivateKey)

    fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
        agePrivateKey: String
    ): String =
        processBuilder
            .command(toDecryptionCommand(gcpAccessToken.value, agePrivateKey))
            .start()
            .run {
                outputStream.buffered().also { it.write(ciphertext.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> result

                    else                                        ->
                        throw SOPSDecryptionException("IOException from decrypting yaml with error code ${exitValue()}: $result")
                }
            }

    fun encrypt(
        text: String,
        config: String,
        gcpAccessToken: GCPAccessToken,
    ): String =
        processBuilder
            .command(toEncryptionCommand(config, gcpAccessToken.value))
            .start()
            .run {
                outputStream.buffered().also { it.write(text.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> result

                    else                                        -> throw SOPSEncryptionException(
                        "IOException from encrypting json with error code ${exitValue()}: $result"
                    )
                }
            }
}
