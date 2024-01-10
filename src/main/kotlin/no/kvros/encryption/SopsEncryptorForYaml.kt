package no.kvros.encryption

import java.io.BufferedReader
import java.io.InputStreamReader

object SopsEncryptorForYaml {
    private val processBuilder =
        ProcessBuilder("sops", "--input-type", "yaml", "--output-type", "json", "--decrypt", "/dev/stdin")
            .redirectErrorStream(true)

    private const val EXECUTION_STATUS_OK = 0

    fun decrypt(ciphertext: String): String? =
        try {
            processBuilder.start().run {
                outputStream.buffered().also { it.write(ciphertext.toByteArray()) }.close()
                when (waitFor()) {
                    EXECUTION_STATUS_OK -> BufferedReader(InputStreamReader(inputStream)).readText()

                    else -> {
                        System.err.println("IOException from decrypting yaml with error code: ${exitValue()}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}
