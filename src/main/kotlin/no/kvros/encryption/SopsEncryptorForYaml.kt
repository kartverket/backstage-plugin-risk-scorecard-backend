package no.kvros.encryption

import java.io.BufferedReader
import java.io.InputStreamReader

data class SopsEncryptorStrategy(
    val keyRingId: String? = null,
    val publicKey: String? = null,
    val provider: SopsEncryptionKeyProvider,
) {
    private val inputTypeYaml = listOf("--input-type", "yaml")
    private val inputTypeJson = listOf("--input-type", "json")
    private val outputTypeYaml = listOf("--output-type", "yaml")
    private val outputTypeJson = listOf("--output-type", "json")
    private val sopsCmd = listOf("sops")

    fun toEncryptionCommand(): List<String> =
        sopsCmd + inputTypeJson + outputTypeYaml + encryptorStrategy() + listOf("--encrypt", "/dev/stdin")

    fun toDecryptionCommand(): List<String> =
        sopsCmd + inputTypeYaml + outputTypeJson + encryptorStrategy() + listOf("--decrypt", "/dev/stdin")

    private fun encryptorStrategy(): List<String> = when (provider) {
        SopsEncryptionKeyProvider.GoogleCloudPlatform -> listOf(provider.sopsCommand, keyRingId!!)
        SopsEncryptionKeyProvider.AGE -> listOf(provider.sopsCommand, publicKey!!)
    }
}

enum class SopsEncryptionKeyProvider(val sopsCommand: String) {
    GoogleCloudPlatform("--gcp-kms"),
    AGE("--age")
}

object SopsEncryptorForYaml {
    private val processBuilder = ProcessBuilder().redirectErrorStream(true)
    private const val EXECUTION_STATUS_OK = 0

    fun decrypt(ciphertext: String, sopsEncryptorStrategy: SopsEncryptorStrategy): String? =
        try {
            processBuilder
                .command(sopsEncryptorStrategy.toDecryptionCommand())
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
        sopsEncryptorStrategy: SopsEncryptorStrategy
    ): String? =
        try {
            processBuilder
                .command(sopsEncryptorStrategy.toEncryptionCommand())
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
