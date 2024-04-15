package no.risc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RiScApplication

fun main(args: Array<String>) {
    runApplication<RiScApplication>(*args)
}
