package no.kvros.validation

import net.pwall.json.schema.JSONSchema
import net.pwall.json.schema.output.BasicOutput

object JSONValidator {
    fun validateJSON(
        schema: String,
        json: String,
    ): BasicOutput =
        // JSONSchema.parseFile(".security/ros_schema_no_v1_0.json").validateBasic(decryptedJson)
        JSONSchema.parse(schema).validateBasic(json)
}
