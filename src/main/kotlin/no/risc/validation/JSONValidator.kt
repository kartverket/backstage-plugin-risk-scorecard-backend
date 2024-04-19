package no.risc.validation

import net.pwall.json.schema.JSONSchema
import net.pwall.json.schema.output.BasicOutput

object JSONValidator {
    fun validateJSON(
        schema: String,
        json: String,
    ): BasicOutput = JSONSchema.parse(schema).validateBasic(json)
}
