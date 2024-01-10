package no.kvros.encryption

import java.io.BufferedReader
import java.io.InputStreamReader

object SopsEncryptorForYaml {
    private val processBuilder = ProcessBuilder().redirectErrorStream(true)

    private const val EXECUTION_STATUS_OK = 0

    fun decrypt(ciphertext: String): String? =
        try {
            processBuilder
                .command("sops", "--input-type", "yaml", "--output-type", "json", "--decrypt", "/dev/stdin")
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
        publicKey: String,
        text: String,
    ): String? =
        try {
            processBuilder
                .command("sops", "--input-type", "json", "--output-type", "yaml", "--encrypt", "--age", publicKey, "/dev/stdin")
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
