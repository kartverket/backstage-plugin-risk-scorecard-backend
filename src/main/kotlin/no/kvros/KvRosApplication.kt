package no.kvros

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KvRosApplication

fun main(args: Array<String>) {
	runApplication<KvRosApplication>(*args)
}
