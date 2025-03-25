package no.risc.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kjson.JSONException
import net.pwall.json.schema.JSONSchema
import net.pwall.json.schema.output.BasicOutput
import no.risc.exception.exceptions.JSONSchemaFetchException
import no.risc.exception.exceptions.RiScNotValidOnFetchException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class SchemaVersion {
    VERSION_3_2,
    VERSION_3_3,
    VERSION_4_0,
    ;

    fun toExpectedString() =
        when (this) {
            VERSION_3_2 -> "3_2"
            VERSION_3_3 -> "3_3"
            VERSION_4_0 -> "4_0"
        }
}

object JSONValidator {
    private val LOGGER: Logger = LoggerFactory.getLogger(JSONValidator::class.java)

    private fun readSchema(
        schemaVersion: String,
        riScId: String,
        isUpdate: Boolean,
    ): String {
        val resourcePath = "schemas/risc_schema_en_v$schemaVersion.json"
        val resource = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)
        return resource?.bufferedReader().use { it?.readText() }
            ?: throw JSONSchemaFetchException(
                message = "Failed to read JSON schema for version $schemaVersion",
                onUpdateOfRiSC = isUpdate,
                riScId = riScId,
            )
    }

    fun getSchemaOnUpdate(
        riScId: String,
        schemaVersion: String,
    ): String = readSchema(schemaVersion = schemaVersion.replace(".", "_"), riScId = riScId, isUpdate = true)

    fun validateAgainstSchema(
        riScId: String,
        schema: String? = null,
        riScContent: String?,
    ): BasicOutput =
        if (riScContent == null) {
            LOGGER.error(
                "RiSc with id: $riScId has riScContent equals null. Probably because of size-limitations of response-body in response.",
            )
            BasicOutput(false)
        } else if (schema === null) {
            SchemaVersion.entries.forEach {
                val currentSchema = readSchema(schemaVersion = it.toExpectedString(), riScId = riScId, isUpdate = false)
                val output = validateJson(riScId, currentSchema, riScContent)
                if (output.valid) {
                    return output
                }
            }
            LOGGER.error("RiSc with id: $riScId failed validation against all schemas.")
            BasicOutput(false)
        } else {
            validateJson(riScId, schema, riScContent)
        }

    private fun validateJson(
        riScId: String,
        schema: String,
        riScContent: String,
    ): BasicOutput =
        try {
            JSONSchema.parse(schema).validateBasic(riScContent)
        } catch (e: JSONException) {
            if (!e.message.contains("Illegal JSON syntax")) {
                throw RiScNotValidOnFetchException(
                    "RiSc with id: $riScId could not be validated against schema",
                    riScId,
                )
            }
            val riscAsJson = yamlToJsonConverter(riScId, riScContent)
            JSONSchema.parse(schema).validateBasic(riscAsJson)
        } catch (e: Exception) {
            throw RiScNotValidOnFetchException("RiSc with id: $riScId could not be validated against schema", riScId)
        }

    private fun yamlToJsonConverter(
        riScId: String,
        yamlString: String,
    ): String =
        try {
            val jsonNode = ObjectMapper(YAMLFactory()).registerKotlinModule().readTree(yamlString)
            ObjectMapper().registerKotlinModule().writeValueAsString(jsonNode)
        } catch (e: Exception) {
            throw RiScNotValidOnFetchException("RiSc with id: $riScId could not be converted from YAML to JSON", riScId)
        }
}
