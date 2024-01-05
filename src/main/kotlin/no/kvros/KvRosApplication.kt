package no.kvros

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File
import no.kvros.utils.decrypt.decryptYamlData

@SpringBootApplication
class KvRosApplication

fun main(args: Array<String>) {
    runApplication<KvRosApplication>(*args)
    val encData = File(".sikkerhet/ros/kryptert.ros.yaml").readText()
    println("encrypted Data: " + encData)
    val decData = decryptYamlData(encData)
    println("decrypted data: " + decData)
}
