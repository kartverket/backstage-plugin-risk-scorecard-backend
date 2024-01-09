package no.kvros.encryption

import java.io.BufferedReader
import java.io.InputStreamReader


object SopsEncryptorForYaml {
    private val processBuilder =
        ProcessBuilder("sops", "--input-type", "yaml", "--output-type", "json", "--decrypt", "/dev/stdin")

    fun decrypt(ciphertext: String): String? {
        //processBuilder.redirectErrorStream(true)

        try {
            val process = processBuilder.start()

            // Write the encrypted data to the process's input stream
            process.outputStream.buffered().use { it.write(ciphertext.toByteArray()) }
            process.outputStream.close()

            // Read the decrypted data from the process's output stream
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val decryptedData = reader.readText()

            // Wait for the process to complete
            process.waitFor()

            return decryptedData
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

}
