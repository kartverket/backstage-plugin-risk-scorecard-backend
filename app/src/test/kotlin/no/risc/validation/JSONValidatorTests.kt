package no.risc.validation

import no.risc.exception.exceptions.RiScNotValidOnFetchException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JSONValidatorTests {
    companion object {
        private val testSchema =
            """
            {
                "type": "object",
                "properties": {
                   "a": {
                       "type": "number"
                   },
                   "b": {
                       "type": "array",
                       "items": {
                            "type": "string"
                       }
                   },
                   "c": {
                       "type": "number" 
                   }
                },
                "required": ["a", "b"]
            }
            """.trimIndent()
    }

    private fun getResource(resourcePath: String): String =
        object {}
            .javaClass.classLoader
            .getResource(resourcePath)
            ?.readText()
            ?: fail("Could not read resource $resourcePath")

    @Test
    fun `test validate against schema without output`() {
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = null)
        assertFalse(output.isValid, "When no risc content is provided, validation should be false")
    }

    @Test
    fun `test validate against unspecified schema with invalid content`() {
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = "{ \"a\": 123 }")
        assertFalse(output.isValid)
    }

    @Test
    fun `test validate against specified schema with invalid content`() {
        val output =
            JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = "{ \"a\": 123 }", schema = testSchema)
        assertFalse(output.isValid)
    }

    @Test
    fun `test validate against specified schema with valid content`() {
        val output =
            JSONValidator.validateAgainstSchema(
                riScId = "abc",
                riScContent = "{ \"a\": 123, \"b\": [\"abc\", \"cde\"] }",
                schema = testSchema,
            )
        assertTrue(output.isValid)
    }

    @Test
    fun `test validate invalid JSON and YAML should throw an error`() {
        assertThrows<RiScNotValidOnFetchException> {
            JSONValidator.validateAgainstSchema(
                riScId = "abc",
                riScContent = "{ 1: 1 ",
            )
        }

        val invalidYAML =
            """
            - a: 1
              - b: 2
             - c: 3
            """.trimIndent()

        assertThrows<RiScNotValidOnFetchException> {
            JSONValidator.validateAgainstSchema(
                riScId = "abc",
                riScContent = invalidYAML,
            )
        }
    }

    @Test
    fun `test validate should work with YAML`() {
        val validYAML =
            """
            a: 1
            b:
                - "abc"
                - "ab"
            """.trimIndent()

        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = validYAML, schema = testSchema)
        assertTrue(output.isValid, "Valid YAML should return a validation output with valid set to true")
    }

    @Test
    fun `test validate version 3_2 without specifying scheme`() {
        val content = getResource("3.2.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content)
        assertTrue(output.isValid, "Content adhering to version 3.2 should successfully be validated")
    }

    @Test
    fun `test retrieve version 3_2 schema and validate version 3_2 against it`() {
        val schema = JSONValidator.getSchemaOnUpdate(riScId = "abc", schemaVersion = "3.2")
        val content = getResource("3.2.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content, schema = schema)
        assertTrue(
            output.isValid,
            "Content adhering to version 3.2 should be successfully validated against the version 3.2 schema.",
        )
    }

    @Test
    fun `test validate version 3_3 without specifying scheme`() {
        val content = getResource("3.3.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content)
        assertTrue(output.isValid, "Content adhering to version 3.3 should successfully be validated")
    }

    @Test
    fun `test retrieve version 3_3 schema and validate version 3_3 against it`() {
        val schema = JSONValidator.getSchemaOnUpdate(riScId = "abc", schemaVersion = "3.3")
        val content = getResource("3.3.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content, schema = schema)
        assertTrue(
            output.isValid,
            "Content adhering to version 3.3 should be successfully validated against the version 3.3 schema.",
        )
    }

    @Test
    fun `test retrieve version 3_3 schema and validate version 4_0 against it`() {
        val schema = JSONValidator.getSchemaOnUpdate(riScId = "abc", schemaVersion = "3.3")
        val content = getResource("4.0.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content, schema = schema)
        assertFalse(
            output.isValid,
            "Content adhering to version 4.0 should fail validation against the version 3.3 schema.",
        )
    }

    @Test
    fun `test validate version 4_0 without specifying scheme`() {
        val content = getResource("4.0.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content)
        assertTrue(output.isValid, "Content adhering to version 4.0 should successfully be validated")
    }

    @Test
    fun `test retrieve version 4_0 schema and validate version 4_0 against it`() {
        val schema = JSONValidator.getSchemaOnUpdate(riScId = "abc", schemaVersion = "4.0")
        val content = getResource("4.0.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content, schema = schema)
        assertTrue(
            output.isValid,
            "Content adhering to version 4.0 should be successfully validated against the version 4.0 schema.",
        )
    }

    @Test
    fun `test retrieve version 4_0 schema and validate version 3_3 against it`() {
        val schema = JSONValidator.getSchemaOnUpdate(riScId = "abc", schemaVersion = "4.0")
        val content = getResource("3.3.json")
        val output = JSONValidator.validateAgainstSchema(riScId = "abc", riScContent = content, schema = schema)
        assertFalse(
            output.isValid,
            "Content adhering to version 3.3 should fail validation against the version 4.0 schema.",
        )
    }

    // må skrive en test her for å validere 4.2 opp mot de andre?
}
