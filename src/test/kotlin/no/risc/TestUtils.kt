package no.risc

import org.junit.jupiter.api.Assertions.fail

fun getResource(resourcePath: String): String =
    object {}
        .javaClass.classLoader
        .getResource(resourcePath)
        ?.readText()
        ?: fail("Could not read resource $resourcePath")
