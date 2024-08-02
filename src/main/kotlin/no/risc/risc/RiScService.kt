package no.risc.risc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.encryption.CryptoServiceIntegration
import no.risc.exception.exceptions.JSONSchemaFetchException
import no.risc.exception.exceptions.RiScNotValidException
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsConfigFetchException
import no.risc.exception.exceptions.UpdatingRiScException
import no.risc.github.GithubConnector
import no.risc.github.GithubContentResponse
import no.risc.github.GithubPullRequestObject
import no.risc.github.GithubRiScIdentifiersResponse
import no.risc.github.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import no.risc.utils.removePathRegex
import no.risc.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.time.measureTimedValue

data class ProcessRiScResultDTO(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
) {
    companion object {
        val INVALID_ACCESS_TOKENS =
            ProcessRiScResultDTO(
                "",
                ProcessingStatus.InvalidAccessTokens,
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
    }
}

@Serializable
data class RiScContentResultDTO(
    val riScId: String,
    val status: ContentStatus,
    val riScStatus: RiScStatus?,
    val riScContent: String?,
    val pullRequestUrl: String? = null,
    val migrationChanges: Boolean? = false,
)

data class PublishRiScResultDTO(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
    val pendingApproval: PendingApprovalDTO?,
)

data class PendingApprovalDTO(
    val pullRequestUrl: String,
    val pullRequestName: String,
)

enum class ContentStatus {
    Success,
    FileNotFound,
    DecryptionFailed,
    Failure,
}

enum class ProcessingStatus(val message: String) {
    ErrorWhenUpdatingRiSc("Error when updating risk scorecard"),
    CreatedRiSc("Created new risk scorecard successfully"),
    UpdatedRiSc("Updated risk scorecard successfully"),
    CreatedPullRequest("Created pull request for risk scorecard"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidAccessTokens("Invalid access tokens"),
}

data class RiScIdentifier(
    val id: String,
    var status: RiScStatus,
    val pullRequestUrl: String? = null,
)

enum class RiScStatus {
    Draft,
    SentForApproval,
    Published,
}

@Service
class RiScService(
    private val githubConnector: GithubConnector,
    @Value("\${filename.prefix}") val filenamePrefix: String,
    private val cryptoService: CryptoServiceIntegration,
) {
    private val logger = LoggerFactory.getLogger(RiScService::class.java)

    suspend fun fetchAllRiScIds(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
    ): GithubRiScIdentifiersResponse =
        githubConnector.fetchAllRiScIdentifiersInRepository(
            owner,
            repository,
            accessTokens.githubAccessToken.value,
        )

    suspend fun fetchAllRiScs(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
    ): List<RiScContentResultDTO> =
        coroutineScope {
            val msId =
                measureTimedValue {
                    githubConnector.fetchAllRiScIdentifiersInRepository(
                        owner,
                        repository,
                        accessTokens.githubAccessToken.value,
                    ).ids
                }
            logger.info("Fetching risc ids took ${msId.duration}")

            val msRiSc =
                measureTimedValue {
                    msId.value.map { id ->
                        async(Dispatchers.IO) {
                            try {
                                val fetchRisc =
                                    when (id.status) {
                                        RiScStatus.Published -> githubConnector::fetchPublishedRiSc
                                        RiScStatus.SentForApproval, RiScStatus.Draft -> githubConnector::fetchDraftedRiScContent
                                    }
                                fetchRisc(owner, repository, id.id, accessTokens.githubAccessToken.value)
                                    .let {
                                        when (id.status) {
                                            RiScStatus.Draft -> {
                                                /*
                                                 * Because of our unusual datastorage, this is an extra state check for Drafts.
                                                 *
                                                 * In case a repository does not delete branches after merging pull requests,
                                                 * our state flowchart would go from SentForApproval (pull request) to
                                                 * Draft (branch exists). Therefore we check if the content in Draft is equal to
                                                 * the content on the default branch (often main). If they are equal then we
                                                 * know we have a ros branch without changes, and therefore it should be in a
                                                 * Published state.
                                                 *  */

                                                // Get ros content on default branch.
                                                val published =
                                                    githubConnector.fetchPublishedRiSc(
                                                        owner,
                                                        repository,
                                                        id.id,
                                                        accessTokens.githubAccessToken.value,
                                                    )

                                                val fileFound = published.status === GithubStatus.Success
                                                val fileisEqual = published.data.equals(it.data)
                                                // Check if file exists and its content is equal.
                                                if (fileFound && fileisEqual) {
                                                    // Set its status to Published.
                                                    id.status = RiScStatus.Published
                                                }
                                                it
                                            }

                                            RiScStatus.SentForApproval -> it
                                            RiScStatus.Published -> it
                                        }
                                    }
                                    .responseToRiScResult(
                                        id.id,
                                        id.status,
                                        accessTokens.gcpAccessToken,
                                        id.pullRequestUrl,
                                    )
                                    .let { migrateToNewMinor(it) }
                                    .let { migrateFrom33To40(it) }
                            } catch (e: Exception) {
                                RiScContentResultDTO(
                                    riScId = id.id,
                                    status = ContentStatus.Failure,
                                    riScStatus = id.status,
                                    riScContent = null,
                                    pullRequestUrl = null,
                                )
                            }
                        }
                    }.awaitAll()
                }

            logger.info("Fetching ${msId.value.count()} RiScs took ${msRiSc.duration}")
            msRiSc.value
        }

    // Update RiSc scenarios from schemaVersion 3.2 to 3.3. This is necessary because 3.3 is backwards compatible,
    // and modifications can only be made when the schemaVersion is 3.3.
    private fun migrateToNewMinor(obj: RiScContentResultDTO): RiScContentResultDTO {
        if (obj.riScContent == null) {
            return obj
        }

        val migratedSchemaVersion = obj.riScContent.replace("\"schemaVersion\": \"3.2\"", "\"schemaVersion\": \"3.3\"")
        return obj.copy(riScContent = migratedSchemaVersion)
    }

    /**
     * Update RiSc content from version 3.3 to 4.0. Includes breaking changes.
     *
     * Changes include:
     * - Bump schemaVersion to 4.0
     *
     * Replace values in vulnerabilities:
     * - User repudiation -> Unmonitored use
     * - Compromised admin user -> Unauthorized access
     * - Escalation of rights -> Unauthorized access
     * - Disclosed secret -> Information leak
     * - Denial of service -> Excessive use
     *
     * Remove "owner" and "deadline" from actions
     * Remove "existingActions" from scenarios
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun migrateFrom33To40(obj: RiScContentResultDTO): RiScContentResultDTO {
        if (obj.riScContent == null) {
            return obj
        }

        var content = obj.riScContent

        val json = Json { ignoreUnknownKeys = true }
        val jsonObject = json.parseToJsonElement(content).jsonObject.toMutableMap()

        // Check if schemaVersion is 3.3, early return the object as it is if not
        if (jsonObject["schemaVersion"]?.jsonPrimitive?.content != "3.3") {
            return obj
        }

        // Replace schemaVersion
        jsonObject["schemaVersion"] = JsonPrimitive("4.0")

        // Update scenarios
        val scenarios = jsonObject["scenarios"]?.jsonArray ?: return obj.copy(riScContent = content)
        val updatedScenarios =
            scenarios.map { scenario ->
                val scenarioObject = scenario.jsonObject.toMutableMap()

                // Remove "existingActions"
                val scenarioDetails = scenarioObject["scenario"]?.jsonObject?.toMutableMap()
                scenarioDetails?.remove("existingActions")

                // Replace values in vulnerabilities array
                val vulnerabilitiesArray = scenarioDetails?.get("vulnerabilities")?.jsonArray?.toMutableList()
                if (vulnerabilitiesArray != null) {
                    val replacementMap =
                        mapOf(
                            "User repudiation" to "Unmonitored use",
                            "Compromised admin user" to "Unauthorized access",
                            "Escalation of rights" to "Unauthorized access",
                            "Disclosed secret" to "Information leak",
                            "Denial of service" to "Excessive use",
                        )

                    val newVulnerabilities =
                        vulnerabilitiesArray
                            .map { it.jsonPrimitive.content }
                            .toMutableSet()

                    replacementMap.forEach { (oldValue, newValue) ->
                        if (newVulnerabilities.contains(oldValue)) {
                            newVulnerabilities.remove(oldValue)
                            newVulnerabilities.add(newValue)
                        }
                    }

                    // Convert back to JsonArray
                    val updatedVulnerabilitiesArray = JsonArray(newVulnerabilities.map { JsonPrimitive(it) })
                    scenarioDetails["vulnerabilities"] = updatedVulnerabilitiesArray
                }

                // Remove "owner" and "deadline" from actions
                val actionsArray = scenarioDetails?.get("actions")?.jsonArray?.toMutableList()
                actionsArray?.forEachIndexed { index, actionElement ->
                    val actionObject = actionElement.jsonObject.toMutableMap()
                    actionObject["action"]?.jsonObject?.toMutableMap()?.apply {
                        this.remove("owner")
                        this.remove("deadline")
                    }?.let { updatedAction ->
                        actionObject["action"] = JsonObject(updatedAction)
                    }
                    actionsArray[index] = JsonObject(actionObject)
                }
                actionsArray?.let { scenarioDetails["actions"] = JsonArray(it) }

                scenarioDetails?.let { scenarioObject["scenario"] = JsonObject(it) }
                JsonObject(scenarioObject)
            }

        jsonObject["scenarios"] = JsonArray(updatedScenarios)

        // Convert the updated JSON object back to string with pretty printing
        val prettyJson =
            Json {
                prettyPrint = true
                prettyPrintIndent = "    "
            }
        content = prettyJson.encodeToString(JsonObject(jsonObject))

        return obj.copy(riScContent = content, migrationChanges = true)
    }

    private fun GithubContentResponse.responseToRiScResult(
        riScId: String,
        riScStatus: RiScStatus,
        gcpAccessToken: GCPAccessToken,
        pullRequestUrl: String?,
    ): RiScContentResultDTO =
        when (status) {
            GithubStatus.Success ->
                try {
                    RiScContentResultDTO(
                        riScId,
                        ContentStatus.Success,
                        riScStatus,
                        decryptContent(gcpAccessToken),
                        pullRequestUrl,
                    )
                } catch (e: Exception) {
                    when (e) {
                        is SOPSDecryptionException ->
                            RiScContentResultDTO(riScId, ContentStatus.DecryptionFailed, riScStatus, e.message)

                        else ->
                            RiScContentResultDTO(riScId, ContentStatus.Failure, riScStatus, null)
                    }
                }

            GithubStatus.NotFound ->
                RiScContentResultDTO(riScId, ContentStatus.FileNotFound, riScStatus, null)

            else ->
                RiScContentResultDTO(riScId, ContentStatus.Failure, riScStatus, null)
        }

    private fun GithubContentResponse.decryptContent(gcpAccessToken: GCPAccessToken) =
        cryptoService.decrypt(
            ciphertext = data(),
            gcpAccessToken = gcpAccessToken,
        )

    suspend fun updateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO = updateOrCreateRiSc(owner, repository, riScId, content, accessTokens)

    suspend fun createRiSc(
        owner: String,
        repository: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        val uniqueRiScId = "$filenamePrefix-${RandomStringUtils.randomAlphanumeric(5)}"
        val result = updateOrCreateRiSc(owner, repository, uniqueRiScId, content, accessTokens)

        return when (result.status) {
            ProcessingStatus.UpdatedRiSc ->
                ProcessRiScResultDTO(
                    uniqueRiScId,
                    ProcessingStatus.CreatedRiSc,
                    "New RiSc was created",
                )

            else -> result
        }
    }

    private suspend fun updateOrCreateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        val jsonSchema =
            githubConnector.fetchJSONSchema("risc_schema_en_v${content.schemaVersion.replace('.', '_')}.json")
        if (jsonSchema.status != GithubStatus.Success) {
            throw JSONSchemaFetchException(
                message =
                    "Failed when fetching JSON schema from Github with status: ${jsonSchema.status}, " +
                        "and error message: ${jsonSchema.data}",
                riScId = riScId,
            )
        }

        val validationStatus = JSONValidator.validateJSON(jsonSchema.data(), content.riSc)
        if (!validationStatus.valid) {
            val validationError = validationStatus.errors?.joinToString("\n") { it.error }.toString()
            throw RiScNotValidException(
                message = "Failed when validating RiSc with error message: $validationError",
                riScId = riScId,
                validationError = validationError,
            )
        }

        val sopsConfig = githubConnector.fetchSopsConfig(owner, repository, accessTokens.githubAccessToken, riScId)
        if (sopsConfig.status != GithubStatus.Success) {
            throw SopsConfigFetchException(
                message = "Failed when fetching SopsConfig from Github with status: ${sopsConfig.status}",
                riScId = riScId,
                responseMessage = "Could not fetch SOPS config",
            )
        }

        val config = removePathRegex(sopsConfig.data())

        val encryptedData: String =
            cryptoService.encrypt(content.riSc, config, accessTokens.gcpAccessToken, riScId)

        try {
            val hasClosedPR =
                githubConnector.updateOrCreateDraft(
                    owner = owner,
                    repository = repository,
                    riScId = riScId,
                    fileContent = encryptedData,
                    requiresNewApproval = content.isRequiresNewApproval,
                    accessTokens = accessTokens,
                    userInfo = content.userInfo,
                )

            return ProcessRiScResultDTO(
                riScId,
                ProcessingStatus.UpdatedRiSc,
                "Risk scorecard was updated" + if (hasClosedPR) " and has to be approved by av risk owner again" else "",
            )
        } catch (e: Exception) {
            throw UpdatingRiScException(
                message = "Failed with error ${e.message} for risk scorecard with id $riScId",
                riScId = riScId,
            )
        }
    }

    fun publishRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessTokens: AccessTokens,
        userInfo: UserInfo,
    ): PublishRiScResultDTO {
        val pullRequestObject =
            githubConnector.createPullRequestForRiSc(
                owner = owner,
                repository = repository,
                riScId = riScId,
                requiresNewApproval = true,
                accessTokens = accessTokens,
                userInfo = userInfo,
            )

        return when (pullRequestObject) {
            is GithubPullRequestObject ->
                PublishRiScResultDTO(
                    riScId,
                    ProcessingStatus.CreatedPullRequest,
                    "Pull request was created",
                    pullRequestObject.toPendingApprovalDTO(),
                )

            else ->
                PublishRiScResultDTO(
                    riScId,
                    ProcessingStatus.ErrorWhenCreatingPullRequest,
                    "Could not create pull request",
                    null,
                )
        }
    }

    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO =
        PendingApprovalDTO(
            pullRequestUrl = this.url,
            pullRequestName = this.head.ref,
        )

    fun fetchLatestJSONSchema(): GithubContentResponse = githubConnector.fetchLatestJSONSchema()
}
