package no.kvros.utils.decrypt

import java.io.BufferedReader
import java.io.InputStreamReader

fun decryptYamlData(encryptedData: String): String? {
    return ProcessBuilder("sops", "--input-type", "yaml", "--output-type", "json", "--decrypt", "/dev/stdin").run {
        redirectErrorStream(true)
        start().run {
            outputStream.buffered().also { it.write(encryptedData.toByteArray()) }.close()
            when (waitFor()) {
                0 -> BufferedReader(InputStreamReader(inputStream)).readText()
                else -> {
                    System.err.println("Failed to decrypt yaml to json")
                    null
                }
            }
        }
    }
}
