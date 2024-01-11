package no.kvros.validation

import net.pwall.json.schema.JSONSchema
import net.pwall.json.schema.output.BasicOutput

object JSONValidator {
    fun validateJSON(decryptedJson: String): BasicOutput =
        JSONSchema.parseFile(".sikkerhet/ros_schema_no_v1_0.json").validateBasic(decryptedJson)
}
