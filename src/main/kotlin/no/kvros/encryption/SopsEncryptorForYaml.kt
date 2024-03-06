package no.kvros.encryption

import java.io.BufferedReader
import java.io.InputStreamReader

data class SopsProviderAndCredentials(
    val provider: SopsEncryptionKeyProvider,
    val publicKeyOrPath: String,
)

data class SopsEncryptorHelper(
    val sopsProvidersAndCredentials: List<SopsProviderAndCredentials>,
) {
    private val sopsCmd = listOf("./our-sops")
    private val encrypt = listOf("encrypt")
    private val decrypt = listOf("decrypt")
    private val inputTypeYaml = listOf("--input-type", "yaml")
    private val inputTypeJson = listOf("--input-type", "json")
    private val outputTypeYaml = listOf("--output-type", "yaml")
    private val outputTypeJson = listOf("--output-type", "json")
    private val encryptConfig = listOf("--encrypt-config")
    private val inputFile = listOf("/dev/stdin")

    fun toEncryptionCommand(config: String): List<String> =
        sopsCmd + encrypt + inputTypeJson + outputTypeYaml + encryptConfig + config + inputFile

    fun toDecryptionCommand(): List<String> = sopsCmd + decrypt + inputTypeYaml + outputTypeJson + inputFile

    private fun encryptWithGcpAndAge(): List<String> {
        val providersAndCredentials = mutableListOf<String>()

        sopsProvidersAndCredentials.map {
            providersAndCredentials.add(it.provider.sopsCommand)
            providersAndCredentials.add(it.publicKeyOrPath)
        }

        return providersAndCredentials
    }

    private fun decryptWithGcp(): List<String> {
        val providersAndCredentials = mutableListOf<String>()

        sopsProvidersAndCredentials.map {
            if (it.provider == SopsEncryptionKeyProvider.GoogleCloudPlatform) {
                providersAndCredentials.add(it.provider.sopsCommand)
                providersAndCredentials.add(it.publicKeyOrPath)
            }
        }

        return providersAndCredentials
    }
}

enum class SopsEncryptionKeyProvider(val sopsCommand: String) {
    GoogleCloudPlatform("--gcp-kms"),
    AGE("--age"),
}

class SOPSDecryptionException(message: String) : Exception(message)

class SOPSEncryptionException(message: String) : Exception(message)

object SopsEncryptorForYaml {
    private val processBuilder = ProcessBuilder().redirectErrorStream(true)
    private const val EXECUTION_STATUS_OK = 0

    fun decrypt(
        ciphertext: String,
        sopsEncryptorHelper: SopsEncryptorHelper,
    ): String =
        processBuilder
            .command(sopsEncryptorHelper.toDecryptionCommand())
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
        sopsEncryptorHelper: SopsEncryptorHelper,
    ): String =
        processBuilder
            .command(sopsEncryptorHelper.toEncryptionCommand(config))
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
