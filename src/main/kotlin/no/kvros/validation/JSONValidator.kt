package no.kvros.validation

import net.pwall.json.schema.JSONSchema

object JSONValidator {
    fun validateJSON(decryptedJson: String): Boolean = JSONSchema.parseFile(".sikkerhet/ros_schema_no_v1_0.json").validate(decryptedJson)
}
