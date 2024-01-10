package no.kvros.validation

import net.pwall.json.schema.JSONSchema

object JSONValidator {
    fun validateJSON(decryptedJson: String): Boolean {
        val output = JSONSchema.parseFile(".sikkerhet/ros_schema_no_v1_0.json").validateBasic(decryptedJson)
        output.errors?.forEach {
            println("${it.error} - ${it.instanceLocation}")
        }
        return output.valid
    }
}
