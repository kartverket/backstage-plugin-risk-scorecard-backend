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

    /**
     * Retrieves the JSON schema for the specified RiSC version from disc.
     *
     * @param schemaVersion The schema version on the format <major>_<minor>
     * @param riScId The ID of the RiSc, for error handling
     * @param isUpdate Indicates if the schema is to be used for validation of an update request, for error handling
     */
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

    /**
     * Retrieves the JSON schema for the specified RiSc version.
     *
     * @param schemaVersion The schema version on the format <major>.<minor>
     * @param riScId The ID of the RiSc, for error handling
     */
    fun getSchemaOnUpdate(
        riScId: String,
        schemaVersion: String,
    ): String = readSchema(schemaVersion = schemaVersion.replace(".", "_"), riScId = riScId, isUpdate = true)

    /**
     * Validates the content of a RiSc against all current version of the RiSC schemas. In this case, the validation is
     * successful if validation succeeds against at least one of the schemas.
     *
     * @param riScId The ID of the RiSc, for error handling
     * @param riScContent The content to validate. Given as a JSON or YAML string.
     */
    fun validateAgainstSchema(
        riScId: String,
        riScContent: String?,
    ): BasicOutput =
        SchemaVersion.entries
            .asSequence()
            .map {
                validateAgainstSchema(
                    riScId = riScId,
                    schema = readSchema(schemaVersion = it.toExpectedString(), riScId = riScId, isUpdate = false),
                    riScContent = riScContent,
                )
            }.filter { it.valid }
            .firstOrNull() ?: BasicOutput(false).also { LOGGER.error("RiSc with id: $riScId failed validation against all schemas.") }

    /**
     * Validates the content of a RiSc against the provided JSON schema.
     *
     * @param riScId The ID of the RiSc, for error handling
     * @param schema A JSON schema to validate against
     * @param riScContent The content to validate. Given as a JSON or YAML string.
     */
    fun validateAgainstSchema(
        riScId: String,
        schema: String,
        riScContent: String?,
    ): BasicOutput {
        if (riScContent == null) {
            LOGGER.error(
                "RiSc with id: $riScId has riScContent equals null. Probably because of size-limitations of response-body in response.",
            )
            return BasicOutput(false)
        }

        try {
            return JSONSchema.parse(schema).validateBasic(riScContent)
        } catch (e: JSONException) {
            if (!e.message.contains("Illegal JSON syntax")) {
                throw RiScNotValidOnFetchException("RiSc with id: $riScId could not be validated against schema", riScId)
            }
            val riscAsJson = yamlToJsonConverter(riScId, riScContent)
            return JSONSchema.parse(schema).validateBasic(riscAsJson)
        } catch (e: Exception) {
            throw RiScNotValidOnFetchException("RiSc with id: $riScId could not be validated against schema", riScId)
        }
    }

    /**
     * Converts the provided YAML to JSON
     *
     * @param yamlString The YAML to be converted
     * @param riScId The ID of the connected RiSC, for error handling
     */
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
