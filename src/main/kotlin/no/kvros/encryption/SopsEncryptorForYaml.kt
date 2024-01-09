package no.kvros.encryption

import java.io.BufferedReader
import java.io.InputStreamReader


object SopsEncryptorForYaml {
    private val processBuilder =
        ProcessBuilder("sops", "--input-type", "yaml", "--output-type", "json", "--decrypt", "/dev/stdin")

    private const val EXECUTION_STATUS_OK = 0

    fun decrypt(ciphertext: String): String? {
        processBuilder.redirectErrorStream(true)

        try {
            val process = processBuilder.start()

            // Write the encrypted data to the process's input stream
            process.outputStream.buffered().use { it.write(ciphertext.toByteArray()) }
            process.outputStream.close()
            // Wait for the process to complete
            process.waitFor()

            if (process.exitValue() != EXECUTION_STATUS_OK) {
                println("IOException from decrypting yaml with error code: ${process.exitValue()}")
                return null
            }

            // Read the decrypted data from the process's output stream
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val decryptedData = reader.readText()

            return decryptedData
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

}
