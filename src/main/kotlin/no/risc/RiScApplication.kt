package no.risc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class RiScApplication

fun main(args: Array<String>) {
    runApplication<RiScApplication>(*args)
}
