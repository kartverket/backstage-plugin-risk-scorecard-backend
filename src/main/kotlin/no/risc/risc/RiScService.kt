package no.risc.risc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import no.risc.encryption.CryptoServiceIntegration
import no.risc.exception.exceptions.CreatingRiScException
import no.risc.exception.exceptions.RiScNotValidOnUpdateException
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.UpdatingRiScException
import no.risc.github.GithubConnector
import no.risc.github.GithubContentResponse
import no.risc.github.GithubPullRequestObject
import no.risc.github.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.initRiSc.InitRiScServiceIntegration
import no.risc.risc.models.DifferenceDTO
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.SopsConfig
import no.risc.risc.models.UserInfo
import no.risc.utils.Difference
import no.risc.utils.DifferenceException
import no.risc.utils.KOffsetDateTimeSerializer
import no.risc.utils.diff
import no.risc.utils.generateRiScId
import no.risc.utils.migrate
import no.risc.validation.JSONValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.HttpCodeStatusMapper
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

class ProcessRiScResultDTO(
    riScId: String,
    status: ProcessingStatus,
    statusMessage: String,
) : RiScResult(riScId, status, statusMessage) {
    companion object {
        val INVALID_ACCESS_TOKENS =
            ProcessRiScResultDTO(
                "",
                ProcessingStatus.InvalidAccessTokens,
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
    }
}

data class CreateRiScResultDTO(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
    val riScContent: String?,
    val sopsConfig: SopsConfig,
)

@Serializable
data class LastPublished(
    @Serializable(KOffsetDateTimeSerializer::class)
    val dateTime: OffsetDateTime,
    val numberOfCommits: Int,
)

@Serializable
data class RiScContentResultDTO(
    val riScId: String,
    val status: ContentStatus,
    val riScStatus: RiScStatus?,
    val riScContent: String?,
    val lastPublished: LastPublished? = null,
    val sopsConfig: SopsConfig? = null,
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

open class RiScResult(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
)

class PublishRiScResultDTO(
    riScId: String,
    status: ProcessingStatus,
    statusMessage: String,
    val pendingApproval: PendingApprovalDTO?,
) : RiScResult(riScId, status, statusMessage)

data class PendingApprovalDTO(
    val pullRequestUrl: String,
    val pullRequestName: String,
)

enum class ContentStatus {
    Success,
    FileNotFound,
    DecryptionFailed,
    Failure,
    NoReadAccess,
    SchemaNotFound,
    SchemaValidationFailed,
}

enum class DifferenceStatus {
    Success,
    GithubFailure,
    GithubFileNotFound,
    JsonFailure,
    DecryptionFailure,
    NoReadAccess,
    SchemaNotFound,
    SchemaValidationFailed,
}

enum class ProcessingStatus(
    val message: String,
) {
    ErrorWhenUpdatingRiSc("Error when updating risk scorecard"),
    CreatedRiSc("Created new risk scorecard successfully"),
    InitializedGeneratedRiSc("Created new auto-generated risk scorecard successfully"),
    UpdatedRiSc("Updated risk scorecard successfully"),
    UpdatedRiScAndCreatedPullRequest("Updated risk scorecard and created pull request"),
    CreatedPullRequest("Created pull request for risk scorecard"),
    OpenedPullRequest("Opened pull request successfully"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidAccessTokens("Invalid access tokens"),
    NoWriteAccessToRepository("Permission denied: You do not have write access to repository"),
    UpdatedRiScRequiresNewApproval("Updated risk scorecard and requires new approval"),
    ErrorWhenCreatingRiSc("Error when creating risk scorecard"),
    AccessTokensValidationFailure("Failure when validating access tokens"),
    ErrorWhenGeneratingInitialRiSc("Error when generating initial risk scorecard"),
    FailedToFetchGcpProjectIds("Failed to fetch GCP project IDs"),
    FailedToCreateSops("Failed to create SOPS configuration"),
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

data class DecryptionFailure(
    val status: ContentStatus,
    val message: String,
)

class InternDifference(
    val status: DifferenceStatus,
    val differenceState: Difference,
    val errorMessage: String = "",
) {
    fun toDTO(date: String = ""): DifferenceDTO =
        DifferenceDTO(
            status = status,
            differenceState = differenceState,
            errorMessage = errorMessage,
            defaultLastModifiedDateString = date,
        )
}

@Service
class RiScService(
    private val githubConnector: GithubConnector,
    @Value("\${filename.prefix}") val filenamePrefix: String,
    private val cryptoService: CryptoServiceIntegration,
    private val initRiScService: InitRiScServiceIntegration,
    private val healthHttpCodeStatusMapper: HttpCodeStatusMapper,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(RiScService::class.java)
    }

    suspend fun fetchAndDiffRiScs(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
        riScId: String,
        headRiSc: String,
    ): DifferenceDTO {
        var lastModifiedDate = ""
        val response: RiScContentResultDTO =
            githubConnector
                .fetchPublishedRiSc(
                    owner = owner,
                    repository = repository,
                    id = riScId,
                    accessToken = accessTokens.githubAccessToken.value,
                ).also {
                    if (it.status == GithubStatus.Success) {
                        lastModifiedDate =
                            it.data
                                .toString()
                                .substringAfterLast("lastmodified: ")
                                .substringBefore("mac")
                                .trimEnd()
                    }
                }.responseToRiScResult(
                    riScId = riScId,
                    riScStatus = RiScStatus.Published,
                    gcpAccessToken = accessTokens.gcpAccessToken,
                    pullRequestUrl = null,
                    lastPublished = null,
                )
        val result: InternDifference =
            when (response.status) {
                ContentStatus.Success -> {
                    try {
                        InternDifference(
                            status = DifferenceStatus.Success,
                            differenceState = diff("${response.riScContent}", headRiSc),
                            errorMessage = "",
                        )
                    } catch (e: DifferenceException) {
                        InternDifference(
                            status = DifferenceStatus.JsonFailure,
                            differenceState = Difference(),
                            errorMessage = "${e.message}",
                        )
                    }
                }

                /*
                This case is considered valid, because if the file is not found, we can assume that the riSc
                does not have a published version yet, and therefore there are no differences to compare.
                The frontend handles this.
                 */
                ContentStatus.FileNotFound ->
                    InternDifference(
                        status = DifferenceStatus.GithubFileNotFound,
                        differenceState = Difference(),
                        errorMessage = "Encountered Github problem: File not found",
                    )

                ContentStatus.DecryptionFailed ->
                    InternDifference(
                        status = DifferenceStatus.DecryptionFailure,
                        differenceState = Difference(),
                        errorMessage = "Encountered ROS problem: Could not decrypt content",
                    )

                ContentStatus.Failure ->
                    InternDifference(
                        status = DifferenceStatus.GithubFailure,
                        differenceState = Difference(),
                        errorMessage = "Encountered Github problem: Github failure",
                    )

                ContentStatus.NoReadAccess ->
                    InternDifference(
                        status = DifferenceStatus.NoReadAccess,
                        differenceState = Difference(),
                        errorMessage = "No read access to repository",
                    )

                ContentStatus.SchemaNotFound ->
                    InternDifference(
                        status = DifferenceStatus.SchemaNotFound,
                        differenceState = Difference(),
                        errorMessage = "Could not fetch JSON schema",
                    )

                ContentStatus.SchemaValidationFailed ->
                    InternDifference(
                        status = DifferenceStatus.SchemaValidationFailed,
                        differenceState = Difference(),
                        errorMessage = "SchemaValidation failed",
                    )
            }
        return result.toDTO(lastModifiedDate)
    }

    suspend fun fetchAllRiScs(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
        latestSupportedVersion: String,
    ): List<RiScContentResultDTO> =
        coroutineScope {
            val riScIds =
                githubConnector
                    .fetchAllRiScIdentifiersInRepository(
                        owner = owner,
                        repository = repository,
                        accessToken = accessTokens.githubAccessToken.value,
                    ).ids
            LOGGER.info("Found RiSc's with id's: ${riScIds.joinToString(", ") { it.id }}")
            val riScContents =
                riScIds
                    .associateWith { id ->
                        async(Dispatchers.IO) {
                            val fetchRiSc =
                                when (id.status) {
                                    RiScStatus.Published -> githubConnector::fetchPublishedRiSc
                                    RiScStatus.SentForApproval, RiScStatus.Draft -> githubConnector::fetchDraftedRiScContent
                                }
                            fetchRiSc(owner, repository, id.id, accessTokens.githubAccessToken.value)
                        }
                    }.mapValues { it.value.await() }

            val riScs =
                riScContents
                    .map { (id, contentResponse) ->

                        async(Dispatchers.IO) {
                            try {
                                val processedContent =
                                    when (id.status) {
                                        RiScStatus.Draft -> {
                                            val publishedContent =
                                                riScContents.entries
                                                    .find {
                                                        it.key.status == RiScStatus.Published && it.key.id == id.id
                                                    }?.value

                                            contentResponse.takeUnless {
                                                publishedContent?.status == GithubStatus.Success &&
                                                    publishedContent.data == contentResponse.data
                                            }
                                        }

                                        RiScStatus.Published -> {
                                            val draftedContent =
                                                riScContents.entries
                                                    .find {
                                                        it.key.status == RiScStatus.Draft && it.key.id == id.id
                                                    }?.value

                                            contentResponse.takeUnless {
                                                draftedContent?.status == GithubStatus.Success &&
                                                    draftedContent.data != contentResponse.data
                                            }
                                        }

                                        else -> {
                                            contentResponse
                                        }
                                    }
                                processedContent?.let { nonNullContent ->
                                    val lastPublished =
                                        githubConnector.fetchLastPublishedRiScDateAndCommitNumber(
                                            owner = owner,
                                            repository = repository,
                                            accessToken = accessTokens.githubAccessToken.value,
                                            riScId = id.id,
                                        )
                                    nonNullContent
                                        .responseToRiScResult(
                                            riScId = id.id,
                                            riScStatus = id.status,
                                            gcpAccessToken = accessTokens.gcpAccessToken,
                                            pullRequestUrl = id.pullRequestUrl,
                                            lastPublished = lastPublished,
                                        ).let { migrate(it, latestSupportedVersion) }
                                }
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
                    .filterNotNull()
                    .map { riScContentResultDTO ->
                        if (riScContentResultDTO.status == ContentStatus.Success) {
                            LOGGER.info(
                                "Validating RiSc with id: '${riScContentResultDTO.riScId}' ${
                                    riScContentResultDTO.riScContent?.let {
                                        "content starting with ${it.substring(3, 10)}***"
                                    } ?: "without content"
                                }",
                            )
                            val validationStatus =
                                JSONValidator.validateAgainstSchema(
                                    riScId = riScContentResultDTO.riScId,
                                    riScContent = riScContentResultDTO.riScContent,
                                )
                            when (validationStatus.valid) {
                                true -> {
                                    LOGGER.info("RiSc with id: ${riScContentResultDTO.riScId} successfully validated")
                                    riScContentResultDTO
                                }

                                false -> {
                                    LOGGER.info("RiSc with id: ${riScContentResultDTO.riScId} failed validation")
                                    RiScContentResultDTO(
                                        riScId = riScContentResultDTO.riScId,
                                        status = ContentStatus.SchemaValidationFailed,
                                        riScStatus = null,
                                        riScContent = null,
                                    )
                                }
                            }
                        } else {
                            riScContentResultDTO
                        }
                    }
            riScs
        }

    private suspend fun GithubContentResponse.responseToRiScResult(
        riScId: String,
        riScStatus: RiScStatus,
        gcpAccessToken: GCPAccessToken,
        pullRequestUrl: String?,
        lastPublished: LastPublished?,
    ): RiScContentResultDTO =
        when (status) {
            GithubStatus.Success ->
                try {
                    val decryptedContent = decryptContent(gcpAccessToken)
                    RiScContentResultDTO(
                        riScId = riScId,
                        status = ContentStatus.Success,
                        riScStatus = riScStatus,
                        riScContent = decryptedContent.riSc,
                        sopsConfig = decryptedContent.sopsConfig,
                        pullRequestUrl = pullRequestUrl,
                        lastPublished = lastPublished,
                    )
                } catch (e: Exception) {
                    LOGGER.error("An error occurred when decrypting: ${e.message}")
                    when (e) {
                        is SOPSDecryptionException ->
                            RiScContentResultDTO(
                                riScId = riScId,
                                status = ContentStatus.DecryptionFailed,
                                riScStatus = riScStatus,
                                riScContent = null,
                            )

                        else ->
                            RiScContentResultDTO(
                                riScId = riScId,
                                status = ContentStatus.Failure,
                                riScStatus = riScStatus,
                                riScContent = null,
                            )
                    }
                }

            GithubStatus.NotFound ->
                RiScContentResultDTO(
                    riScId = riScId,
                    status = ContentStatus.FileNotFound,
                    riScStatus = riScStatus,
                    riScContent = null,
                )

            else ->
                RiScContentResultDTO(
                    riScId = riScId,
                    status = ContentStatus.Failure,
                    riScStatus = riScStatus,
                    riScContent = null,
                )
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
        defaultBranch: String,
    ): RiScResult =
        updateOrCreateRiSc(
            owner = owner,
            repository = repository,
            riScId = riScId,
            content = content,
            sopsConfig = content.sopsConfig,
            accessTokens = accessTokens,
            defaultBranch = defaultBranch,
        )

    suspend fun createRiSc(
        owner: String,
        repository: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
        defaultBranch: String,
        generateDefault: Boolean,
    ): CreateRiScResultDTO {
        val uniqueRiScId = generateRiScId(filenamePrefix)
        LOGGER.info("Generating default content")
        val riScContentWrapperObject =
            content.copy(
                riSc =
                    if (generateDefault) {
                        initRiScService.generateDefaultRiSc(repository, content.riSc)
                    } else {
                        content.riSc
                    },
            )
        LOGGER.info("Generated default content")
        try {
            val result =
                updateOrCreateRiSc(
                    owner = owner,
                    repository = repository,
                    riScId = uniqueRiScId,
                    content = riScContentWrapperObject,
                    sopsConfig = content.sopsConfig,
                    accessTokens = accessTokens,
                    defaultBranch = defaultBranch,
                )

            if (result.status == ProcessingStatus.UpdatedRiSc) {
                return CreateRiScResultDTO(
                    riScId = uniqueRiScId,
                    status = ProcessingStatus.CreatedRiSc,
                    statusMessage = "New RiSc was created",
                    riScContent = riScContentWrapperObject.riSc,
                    sopsConfig = riScContentWrapperObject.sopsConfig,
                )
            } else {
                throw CreatingRiScException(
                    message = "Failed to create RiSc with id $uniqueRiScId",
                    riScId = uniqueRiScId,
                )
            }
        } catch (e: Exception) {
            throw CreatingRiScException(
                message = "${e.message} for risk scorecard with id $uniqueRiScId",
                riScId = uniqueRiScId,
            )
        }
    }

    private suspend fun updateOrCreateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        sopsConfig: SopsConfig,
        accessTokens: AccessTokens,
        defaultBranch: String,
    ): RiScResult {
        val validationStatus =
            JSONValidator.validateAgainstSchema(
                riScId = riScId,
                schema = JSONValidator.getSchemaOnUpdate(riScId, content.schemaVersion),
                riScContent = content.riSc,
            )
        if (!validationStatus.valid) {
            val validationError = validationStatus.errors?.joinToString("\n") { it.error }.toString()
            throw RiScNotValidOnUpdateException(
                message = "Failed when validating RiSc with error message: $validationError",
                riScId = riScId,
                validationError = validationError,
            )
        }

        val encryptedData: String =
            cryptoService.encrypt(
                text = content.riSc,
                sopsConfig = sopsConfig,
                gcpAccessToken = accessTokens.gcpAccessToken,
                riScId = riScId,
            )

        try {
            val riScApprovalPRStatus =
                githubConnector.updateOrCreateDraft(
                    owner = owner,
                    repository = repository,
                    riScId = riScId,
                    fileContent = encryptedData,
                    requiresNewApproval = content.isRequiresNewApproval,
                    accessTokens = accessTokens,
                    userInfo = content.userInfo,
                    defaultBranch = defaultBranch,
                )

            return when (riScApprovalPRStatus.pullRequest) {
                is GithubPullRequestObject ->
                    PublishRiScResultDTO(
                        riScId = riScId,
                        status = ProcessingStatus.UpdatedRiScAndCreatedPullRequest,
                        statusMessage = "RiSc was updated and does not require approval - pull request was created",
                        pendingApproval = riScApprovalPRStatus.pullRequest.toPendingApprovalDTO(),
                    )

                null ->
                    ProcessRiScResultDTO(
                        riScId = riScId,
                        status =
                            if (riScApprovalPRStatus.hasClosedPr) {
                                ProcessingStatus.UpdatedRiScRequiresNewApproval
                            } else {
                                ProcessingStatus.UpdatedRiSc
                            },
                        statusMessage =
                            "Risk scorecard was updated" +
                                if (riScApprovalPRStatus.hasClosedPr) {
                                    " and has to be approved by av risk owner again"
                                } else {
                                    ""
                                },
                    )

                else -> {
                    throw UpdatingRiScException(
                        message = "Failed to update risk scorecard with id $riScId",
                        riScId = riScId,
                    )
                }
            }
        } catch (e: Exception) {
            throw UpdatingRiScException(
                message = "Failed with error ${e.message} for risk scorecard with id $riScId",
                riScId = riScId,
            )
        }
    }

    suspend fun publishRiSc(
        owner: String,
        repository: String,
        riScId: String,
        gitHubAccessToken: String,
        userInfo: UserInfo,
        baseBranch: String,
    ): PublishRiScResultDTO {
        val pullRequestObject =
            githubConnector.createPullRequestForRiSc(
                owner = owner,
                repository = repository,
                riScId = riScId,
                requiresNewApproval = true,
                gitHubAccessToken = gitHubAccessToken,
                userInfo = userInfo,
                baseBranch = baseBranch,
            )

        return PublishRiScResultDTO(
            riScId = riScId,
            status = ProcessingStatus.CreatedPullRequest,
            statusMessage = "Pull request was created",
            pendingApproval = pullRequestObject.toPendingApprovalDTO(),
        )
    }

    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO =
        PendingApprovalDTO(
            pullRequestUrl = this.url,
            pullRequestName = this.head.ref,
        )
}
