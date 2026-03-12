package no.risc.crypto.sops.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@JsonIgnoreProperties(ignoreUnknown = true)
data class SopsConfig(
    // Uses exact SOPS config field names to work with both Jackson YAML and kotlinx.serialization
    val shamir_threshold: Int,
    val key_groups: List<KeyGroup>? = emptyList(),
    val kms: List<JsonElement>? = null,
    val gcp_kms: List<GcpKmsEntry>? = emptyList(),
    val age: List<AgeEntry>? = null,
    val lastmodified: String? = null,
    val mac: String? = null,
    val unencrypted_suffix: String? = null,
    val version: String? = null,
)

@Serializable
data class GcpKmsEntry(
    val resource_id: String,
    val created_at: String? = null,
    val enc: String? = null,
)

@Serializable
data class AgeEntry(
    val recipient: String,
    val enc: String? = null,
)

@Serializable
data class KeyGroup(
    val gcp_kms: List<GcpKmsEntry>? = null,
    val hc_vault: List<JsonElement>? = null,
    val age: List<AgeEntry>? = null,
)
