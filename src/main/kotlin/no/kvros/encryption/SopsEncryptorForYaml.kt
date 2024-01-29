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
    private val inputTypeYaml = listOf("--input-type", "yaml")
    private val inputTypeJson = listOf("--input-type", "json")
    private val outputTypeYaml = listOf("--output-type", "yaml")
    private val outputTypeJson = listOf("--output-type", "json")
    private val sopsCmd = listOf("sops")

    fun toEncryptionCommand(): List<String> =
        sopsCmd + inputTypeJson + outputTypeYaml + encryptWithGcpAndAge() + listOf("--encrypt", "/dev/stdin")

    fun toDecryptionCommand(): List<String> =
        sopsCmd + inputTypeYaml + outputTypeJson + decryptWithGcp() + listOf("--decrypt", "/dev/stdin")

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

object SopsEncryptorForYaml {
    private val processBuilder = ProcessBuilder().redirectErrorStream(true)
    private const val EXECUTION_STATUS_OK = 0

    fun decrypt(
        ciphertext: String,
        sopsEncryptorHelper: SopsEncryptorHelper,
    ): String? =
        try {
            processBuilder
                .command(sopsEncryptorHelper.toDecryptionCommand())
                .start()
                .run {
                    outputStream.buffered().also { it.write(ciphertext.toByteArray()) }.close()
                    val result = BufferedReader(InputStreamReader(inputStream)).readText()
                    when (waitFor()) {
                        EXECUTION_STATUS_OK -> result

                        else -> {
                            System.err.println("IOException from decrypting yaml with error code ${exitValue()}: $result")
                            null
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    fun encrypt(
        text: String,
        sopsEncryptorHelper: SopsEncryptorHelper,
    ): String? =
        try {
            println(sopsEncryptorHelper.toEncryptionCommand())
            processBuilder
                .command(sopsEncryptorHelper.toEncryptionCommand())
                .start()
                .run {
                    outputStream.buffered().also { it.write(text.toByteArray()) }.close()
                    val result = BufferedReader(InputStreamReader(inputStream)).readText()
                    when (waitFor()) {
                        EXECUTION_STATUS_OK -> result

                        else -> {
                            System.err.println("IOException from encrypting json with error code ${exitValue()}: $result")
                            null
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}
