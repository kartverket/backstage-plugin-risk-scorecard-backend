package no.risc.validation

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.OutputFormat
import com.networknt.schema.SpecVersion
import com.networknt.schema.output.OutputUnit
import no.risc.exception.exceptions.JSONSchemaFetchException
import no.risc.exception.exceptions.RiScNotValidOnFetchException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class SchemaVersion {
    VERSION_3_2,
    VERSION_3_3,
    VERSION_4_0,
    VERSION_4_1, ;

    fun toExpectedString() =
        when (this) {
            VERSION_3_2 -> "3_2"
            VERSION_3_3 -> "3_3"
            VERSION_4_0 -> "4_0"
            VERSION_4_1 -> "4_1"
        }
}

object JSONValidator {
    private val LOGGER: Logger = LoggerFactory.getLogger(JSONValidator::class.java)
    private val schemaFactory: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

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
    ): OutputUnit =
        SchemaVersion.entries
            .asSequence()
            .map {
                validateAgainstSchema(
                    riScId = riScId,
                    schema = readSchema(schemaVersion = it.toExpectedString(), riScId = riScId, isUpdate = false),
                    riScContent = riScContent,
                )
            }.filter { it.isValid }
            .firstOrNull()
            ?: OutputUnit().also {
                it.isValid = false
                LOGGER.error("RiSc with id: $riScId failed validation against all schemas.")
            }

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
    ): OutputUnit {
        if (riScContent == null) {
            LOGGER.error(
                "RiSc with id: $riScId has riScContent equal to null. Probably because of size-limitations of response-body in response.",
            )
            return OutputUnit().also { it.isValid = false }
        }

        try {
            return try {
                schemaFactory.getSchema(schema).validate(riScContent, InputFormat.JSON, OutputFormat.LIST)
            } catch (e: IllegalArgumentException) {
                schemaFactory.getSchema(schema).validate(riScContent, InputFormat.YAML, OutputFormat.LIST)
            }
        } catch (e: Exception) {
            throw RiScNotValidOnFetchException("RiSc with id: $riScId could not be validated against schema", riScId)
        }
    }
}
