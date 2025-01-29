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
import no.risc.exception.exceptions.SopsConfigFetchException
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
import no.risc.risc.models.UserInfo
import no.risc.utils.Difference
import no.risc.utils.DifferenceException
import no.risc.utils.diff
import no.risc.utils.generateRiScId
import no.risc.utils.migrate
import no.risc.validation.JSONValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.HttpCodeStatusMapper
import org.springframework.stereotype.Service

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
)

@Serializable
data class RiScContentResultDTO(
    val riScId: String,
    val status: ContentStatus,
    val riScStatus: RiScStatus?,
    val riScContent: String?,
    val sopsConfig: String? = null,
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
    CreatedSops("Created SOPS configuration successfully"),
    InitializedGeneratedRiSc("Created new auto-generated risk scorecard successfully"),
    UpdatedRiSc("Updated risk scorecard successfully"),
    UpdatedSops("Updated SOPS configuration successfully"),
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
    NoGcpKeyInSopsConfigFound("No GCP KMS resource ID was found in sops config"),
    FetchedSopsConfig("Fetched sops config successfully"),
    FailedToFetchGcpProjectIds("Failed to fetch GCP project IDs"),
    FailedToCreateSops("Failed to create SOPS configuration"),
    NoSopsConfigFound("No SOPS config found in repo"),
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
                )
        val result: InternDifference =
            when (response.status) {
                ContentStatus.Success -> {
                    try {
                        InternDifference(
                            status = DifferenceStatus.Success,
                            differenceState = diff("${response.riScContent}", headRiSc),
                            "",
                        )
                    } catch (e: DifferenceException) {
                        InternDifference(
                            status = DifferenceStatus.JsonFailure,
                            Difference(),
                            "${e.message}",
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
                        "Encountered Github problem: File not found",
                    )

                ContentStatus.DecryptionFailed ->
                    InternDifference(
                        status = DifferenceStatus.DecryptionFailure,
                        differenceState = Difference(),
                        "Encountered ROS problem: Could not decrypt content",
                    )

                ContentStatus.Failure ->
                    InternDifference(
                        status = DifferenceStatus.GithubFailure,
                        differenceState = Difference(),
                        "Encountered Github problem: Github failure",
                    )

                ContentStatus.NoReadAccess ->
                    InternDifference(
                        status = DifferenceStatus.NoReadAccess,
                        differenceState = Difference(),
                        "No read access to repository",
                    )

                ContentStatus.SchemaNotFound ->
                    InternDifference(
                        status = DifferenceStatus.SchemaNotFound,
                        differenceState = Difference(),
                        "Could not fetch JSON schema",
                    )

                ContentStatus.SchemaValidationFailed ->
                    InternDifference(
                        status = DifferenceStatus.SchemaValidationFailed,
                        differenceState = Difference(),
                        "SchemaValidation failed",
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
                        owner,
                        repository,
                        accessTokens.githubAccessToken.value,
                    ).ids
            LOGGER.info("Found RiSc's with id's: ${riScIds.map { it.id }.joinToString(", ")}")
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
                                    nonNullContent
                                        .responseToRiScResult(
                                            id.id,
                                            id.status,
                                            accessTokens.gcpAccessToken,
                                            id.pullRequestUrl,
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
                    .map {
                        if (it.status == ContentStatus.Success) {
                            LOGGER.info(
                                "Validating RiSc with id: '${it.riScId}' ${
                                    it.riScContent?.let {
                                        "content starting with ${it.substring(3, 10)}***"
                                    } ?: "without content"
                                }",
                            )
                            val validationStatus =
                                JSONValidator.validateAgainstSchema(
                                    riScId = it.riScId,
                                    riScContent = it.riScContent,
                                )
                            when (validationStatus.valid) {
                                true -> {
                                    LOGGER.info("RiSc with id: ${it.riScId} successfully validated")
                                    it
                                }

                                false -> {
                                    LOGGER.info("RiSc with id: ${it.riScId} failed validation")
                                    RiScContentResultDTO(
                                        it.riScId,
                                        ContentStatus.SchemaValidationFailed,
                                        null,
                                        null,
                                    )
                                }
                            }
                        } else {
                            it
                        }
                    }
            riScs
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
                    val decryptedContent = decryptContent(gcpAccessToken)
                    RiScContentResultDTO(
                        riScId,
                        ContentStatus.Success,
                        riScStatus,
                        decryptedContent.first,
                        decryptedContent.second,
                        pullRequestUrl,
                    )
                } catch (e: Exception) {
                    LOGGER.error("An error occured when decrypting: ${e.message}")
                    when (e) {
                        is SOPSDecryptionException ->
                            RiScContentResultDTO(riScId, ContentStatus.DecryptionFailed, riScStatus, null)

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
        val riScContentWrapperObject =
            content.copy(
                riSc =
                    if (generateDefault) {
                        initRiScService.generateDefaultRiSc(repository, content.riSc)
                    } else {
                        content.riSc
                    },
            )
        return try {
            val result =
                updateOrCreateRiSc(
                    owner,
                    repository,
                    uniqueRiScId,
                    riScContentWrapperObject,
                    content.sopsConfig,
                    accessTokens,
                    defaultBranch,
                )

            if (result.status == ProcessingStatus.UpdatedRiSc) {
                return CreateRiScResultDTO(
                    uniqueRiScId,
                    ProcessingStatus.CreatedRiSc,
                    "New RiSc was created",
                    riScContentWrapperObject.riSc,
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
        sopsConfig: String,
        accessTokens: AccessTokens,
        defaultBranch: String,
    ): RiScResult {
        val validationStatus =
            JSONValidator.validateAgainstSchema(
                riScId,
                JSONValidator.getSchemaOnUpdate(riScId, content),
                content.riSc,
            )
        if (!validationStatus.valid) {
            val validationError = validationStatus.errors?.joinToString("\n") { it.error }.toString()
            throw RiScNotValidOnUpdateException(
                message = "Failed when validating RiSc with error message: $validationError",
                riScId = riScId,
                validationError = validationError,
            )
        }

        if (sopsConfig.isEmpty()) {
            throw SopsConfigFetchException(
                message = "Failed to read SOPS config",
                riScId = riScId,
                responseMessage = "Did not receive SOPS config",
            )
        }

        val encryptedData: String =
            cryptoService.encrypt(content.riSc, sopsConfig, accessTokens.gcpAccessToken, riScId)

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
                        riScId,
                        status = ProcessingStatus.UpdatedRiScAndCreatedPullRequest,
                        "RiSc was updated and does not require approval - pull request was created",
                        riScApprovalPRStatus.pullRequest.toPendingApprovalDTO(),
                    )

                null ->
                    ProcessRiScResultDTO(
                        riScId,
                        status =
                            if (riScApprovalPRStatus.hasClosedPr) {
                                ProcessingStatus.UpdatedRiScRequiresNewApproval
                            } else {
                                ProcessingStatus.UpdatedRiSc
                            },
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
