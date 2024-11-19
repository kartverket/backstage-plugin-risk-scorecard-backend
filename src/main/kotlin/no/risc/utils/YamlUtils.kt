package no.risc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object YamlUtils {
    val objectMapper = ObjectMapper(YAMLFactory()).apply { registerKotlinModule() }

    inline fun <reified T> to(yamlString: String) = objectMapper.readValue(yamlString, T::class.java)
}
