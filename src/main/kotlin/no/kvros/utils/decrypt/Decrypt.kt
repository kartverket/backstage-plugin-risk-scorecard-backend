package no.kvros.utils.decrypt

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun decryptYamlData(encryptedData: String): String? {

    val encData = File(".sikkerhet/ros/kryptert.ros.yaml").readText()
    println("encData:::: " + encData)

    val processBuilder = ProcessBuilder("sops", "--input-type", "yaml", "--output-type", "yaml", "--decrypt", "/dev/stdin")
    processBuilder.redirectErrorStream(true)

    try {
        val process = processBuilder.start()

        // Write the encrypted data to the process's input stream
        process.outputStream.buffered().use { it.write(encryptedData.toByteArray()) }
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