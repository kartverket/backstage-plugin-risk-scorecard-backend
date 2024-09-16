package no.risc.risc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import no.risc.encryption.CryptoServiceIntegration
import no.risc.exception.exceptions.CreatingRiScException
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
import no.risc.utils.migrate
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
    val migrationStatus: MigrationStatus =
        MigrationStatus(
            migrationChanges = false,
            migrationRequiresNewApproval = false,
            migrationVersions =
                MigrationVersions(
                    fromVersion = null,
                    toVersion = null,
                ),
        ),
)

@Serializable
data class MigrationStatus(
    val migrationChanges: Boolean,
    val migrationRequiresNewApproval: Boolean,
    val migrationVersions: MigrationVersions,
)

@Serializable
data class MigrationVersions(
    var fromVersion: String?,
    var toVersion: String?,
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

enum class DifferenceStatus {
    Success,
    GithubFailure,
    JsonFailure,
    DecryptionFailure,
}

enum class ProcessingStatus(val message: String) {
    ErrorWhenUpdatingRiSc("Error when updating risk scorecard"),
    CreatedRiSc("Created new risk scorecard successfully"),
    UpdatedRiSc("Updated risk scorecard successfully"),
    CreatedPullRequest("Created pull request for risk scorecard"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidAccessTokens("Invalid access tokens"),
    UpdatedRiScRequiresNewApproval("Updated risk scorecard and requires new approval"),
    ErrorWhenCreatingRiSc("Error when creating risk scorecard"),
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

    suspend fun fetchDefaultRiSc(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
        riScId: String,
    ): RiScContentResultDTO {
        return githubConnector.fetchPublishedRiSc(
            owner = owner,
            repository = repository,
            id = riScId,
            accessToken = accessTokens.githubAccessToken.value,
        ).responseToRiScResult(
            riScId = riScId,
            riScStatus = RiScStatus.Published,
            gcpAccessToken = accessTokens.gcpAccessToken,
            pullRequestUrl = null,
        )
    }

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
        latestSupportedVersion: String,
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

            val riScContents =
                msId.value.associateWith { id ->
                    async(Dispatchers.IO) {
                        val fetchRiSc =
                            when (id.status) {
                                RiScStatus.Published -> githubConnector::fetchPublishedRiSc
                                RiScStatus.SentForApproval, RiScStatus.Draft -> githubConnector::fetchDraftedRiScContent
                            }
                        fetchRiSc(owner, repository, id.id, accessTokens.githubAccessToken.value)
                    }
                }.mapValues { it.value.await() }

            val msRiSc =
                measureTimedValue {
                    riScContents.map { (id, contentResponse) ->
                        async(Dispatchers.IO) {
                            try {
                                val processedContent =
                                    if (id.status == RiScStatus.Draft) {
                                        val publishedContent =
                                            riScContents.entries.find {
                                                it.key.status == RiScStatus.Published && it.key.id == id.id
                                            }?.value

                                        if (publishedContent?.status == GithubStatus.Success &&
                                            publishedContent.data == contentResponse.data
                                        ) {
                                            id.status = RiScStatus.Published
                                        }
                                        contentResponse
                                    } else {
                                        contentResponse
                                    }

                                processedContent
                                    .responseToRiScResult(id.id, id.status, accessTokens.gcpAccessToken, id.pullRequestUrl)
                                    .let { migrate(it, latestSupportedVersion) }
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

    private suspend fun GithubContentResponse.responseToRiScResult(
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

    private suspend fun GithubContentResponse.decryptContent(gcpAccessToken: GCPAccessToken) =
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
        try {
            val result = updateOrCreateRiSc(owner, repository, uniqueRiScId, content, accessTokens)

            if (result.status == ProcessingStatus.UpdatedRiSc) {
                return ProcessRiScResultDTO(
                    uniqueRiScId,
                    ProcessingStatus.CreatedRiSc,
                    "New RiSc was created",
                )
            }
        } catch (e: Exception) {
            throw CreatingRiScException(
                message = "${e.message} for risk scorecard with id $uniqueRiScId",
                riScId = uniqueRiScId,
            )
        }
        return ProcessRiScResultDTO.INVALID_ACCESS_TOKENS
    }

    private suspend fun updateOrCreateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        val resourcePath = "schemas/risc_schema_en_v${content.schemaVersion.replace('.', '_')}.json"
        val resource = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)
        val jsonSchema =
            resource?.bufferedReader().use { reader ->
                reader?.readText() ?: throw JSONSchemaFetchException(
                    message = "Failed to read JSON schema for version ${content.schemaVersion}",
                    riScId = riScId,
                )
            }

        val validationStatus = JSONValidator.validateJSON(jsonSchema, content.riSc)
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
                status = if (hasClosedPR) ProcessingStatus.UpdatedRiScRequiresNewApproval else ProcessingStatus.UpdatedRiSc,
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
}
