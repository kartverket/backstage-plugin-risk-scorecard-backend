package no.risc.risc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.risc.encryption.CryptoServiceIntegration
import no.risc.exception.exceptions.CreatingRiScException
import no.risc.exception.exceptions.DifferenceException
import no.risc.exception.exceptions.RiScNotValidOnUpdateException
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.UpdatingRiScException
import no.risc.github.GithubConnector
import no.risc.github.RiScGithubMetadata
import no.risc.github.chooseRiScContentFromStatus
import no.risc.github.getRiScStatus
import no.risc.github.models.GithubContentResponse
import no.risc.github.models.GithubPullRequestObject
import no.risc.github.models.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.initRiSc.InitRiScServiceIntegration
import no.risc.risc.models.ContentStatus
import no.risc.risc.models.CreateRiScResultDTO
import no.risc.risc.models.DefaultRiScType
import no.risc.risc.models.DeleteRiScResultDTO
import no.risc.risc.models.DifferenceDTO
import no.risc.risc.models.DifferenceStatus
import no.risc.risc.models.InternDifference
import no.risc.risc.models.LastPublished
import no.risc.risc.models.PendingApprovalDTO
import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus
import no.risc.risc.models.PublishRiScResultDTO
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiScContentResultDTO
import no.risc.risc.models.RiScResult
import no.risc.risc.models.RiScStatus
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.SopsConfig
import no.risc.risc.models.UserInfo
import no.risc.rosa.RosaIntegration
import no.risc.utils.comparison.compare
import no.risc.utils.generateRiScId
import no.risc.utils.migrate
import no.risc.utils.tryOrDefaultWithErrorLogging
import no.risc.validation.JSONValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class RiScService(
    private val githubConnector: GithubConnector,
    @Value("\${filename.prefix}") val filenamePrefix: String,
    private val cryptoService: CryptoServiceIntegration,
    private val initRiScService: InitRiScServiceIntegration,
    private val rosaClient: RosaIntegration,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(RiScService::class.java)
    }

    /**
     * Compares the provided draft content of a RiSc with the current published content of the same RiSc.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository the RiSc is in.
     * @param accessTokens The access tokens to use for authorization.
     * @param riScId The ID of the RiSc.
     * @param draftRiScContent The draft content of the RiSc to compare with.
     */
    suspend fun fetchAndDiffRiSc(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
        riScId: String,
        draftRiScContent: String,
    ): DifferenceDTO =
        githubConnector
            .fetchPublishedRiSc(
                owner = owner,
                repository = repository,
                id = riScId,
                accessToken = accessTokens.githubAccessToken.value,
            ).responseToRiScResult(
                riScId = riScId,
                riScStatus = RiScStatus.Published,
                gcpAccessToken = accessTokens.gcpAccessToken,
                lastPublished =
                    githubConnector.fetchLastPublishedRiScDateAndCommitNumber(
                        owner = owner,
                        repository = repository,
                        accessToken = accessTokens.githubAccessToken.value,
                        riScId = riScId,
                    ),
            ).let { response ->
                when (response.status) {
                    ContentStatus.Success -> {
                        try {
                            InternDifference(
                                status = DifferenceStatus.Success,
                                differenceState =
                                    compare(
                                        updatedRiSc = RiSc.fromContent(draftRiScContent),
                                        oldRiSc = RiSc.fromContent(response.riScContent),
                                        lastPublished = response.lastPublished,
                                    ),
                            )
                        } catch (e: DifferenceException) {
                            InternDifference(status = DifferenceStatus.JsonFailure, errorMessage = "${e.message}")
                        }
                    }

                    /**
                     * This case is considered valid, because if the file is not found, we can assume that the riSc
                     * does not have a published version yet, and therefore there are no differences to compare.
                     * The frontend handles this.
                     */
                    ContentStatus.FileNotFound ->
                        InternDifference(
                            status = DifferenceStatus.GithubFileNotFound,
                            errorMessage = "Encountered Github problem: File not found",
                        )

                    ContentStatus.DecryptionFailed ->
                        InternDifference(
                            status = DifferenceStatus.DecryptionFailure,
                            errorMessage = "Encountered ROS problem: Could not decrypt content",
                        )

                    ContentStatus.Failure ->
                        InternDifference(
                            status = DifferenceStatus.GithubFailure,
                            errorMessage = "Encountered Github problem: Github failure",
                        )

                    ContentStatus.NoReadAccess ->
                        InternDifference(
                            status = DifferenceStatus.NoReadAccess,
                            errorMessage = "No read access to repository",
                        )

                    ContentStatus.SchemaNotFound ->
                        InternDifference(
                            status = DifferenceStatus.SchemaNotFound,
                            errorMessage = "Could not fetch JSON schema",
                        )

                    ContentStatus.SchemaValidationFailed ->
                        InternDifference(
                            status = DifferenceStatus.SchemaValidationFailed,
                            errorMessage = "SchemaValidation failed",
                        )
                }.toDTO(response.sopsConfig?.lastModified ?: "")
            }

    /**
     * Fetches all RiScs in the given repository. There are three types, drafts (RiScs that have pending updates), sent
     * for approval (RiScs that have pending pull requests) and published (RiScs that have been approved, i.e., appear
     * in the default branch of the repository). If there exists multiple version of a RiSc with the same ID, they are
     * prioritised to return the most updated RiSc. Each fetched RiSc is migrated to the latest supported version and
     * validated against the JSON schema of their version.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to fetch RiScs from.
     * @param accessTokens The access tokens to use for authorization.
     * @param latestSupportedVersion The RiSc schema version to migrate the RiScs to if not already or past this version.
     */
    suspend fun fetchAllRiScs(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
        latestSupportedVersion: String,
    ): List<RiScContentResultDTO> =
        coroutineScope {
            val riScGithubMetadataList: List<RiScGithubMetadata> =
                githubConnector.fetchRiScGithubMetadata(
                    owner,
                    repository,
                    accessTokens.githubAccessToken,
                )

            riScGithubMetadataList
                .map { riScMetadata ->
                    async(Dispatchers.IO) {
                        try {
                            val riScContents =
                                githubConnector.fetchBranchAndMainRiScContent(
                                    riScMetadata.id,
                                    owner,
                                    repository,
                                    accessTokens.githubAccessToken,
                                )

                            val riScStatus =
                                getRiScStatus(
                                    riScMetadata,
                                    riScContents.mainContent,
                                    riScContents.branchContent,
                                )

                            val riScToReturn: GithubContentResponse =
                                chooseRiScContentFromStatus(
                                    riScStatus,
                                    riScContents.branchContent,
                                    riScContents.mainContent,
                                )

                            riScToReturn.responseToRiScResult(
                                riScMetadata.id,
                                riScStatus,
                                accessTokens.gcpAccessToken,
                                lastPublished =
                                    githubConnector.fetchLastPublishedRiScDateAndCommitNumber(
                                        owner = owner,
                                        repository = repository,
                                        accessToken = accessTokens.githubAccessToken.value,
                                        riScId = riScMetadata.id,
                                    ),
                                pullRequestUrl = riScMetadata.prUrl,
                            )
                        } catch (_: Exception) {
                            RiScContentResultDTO(
                                riScId = riScMetadata.id,
                                status = ContentStatus.Failure,
                                riScStatus = RiScStatus.Deleted,
                                riScContent = null,
                                pullRequestUrl = null,
                            )
                        }
                    }
                }.awaitAll()
                .filter { it.riScStatus != RiScStatus.Deleted }
                // Validate RiSc against JSON schema
                .map { riScContentResultDTO ->
                    if (riScContentResultDTO.status == ContentStatus.Success) {
                        LOGGER.info("Validating RiSc with id '${riScContentResultDTO.riScId}.")
                        val validationStatus =
                            JSONValidator.validateAgainstSchema(
                                riScId = riScContentResultDTO.riScId,
                                riScContent = riScContentResultDTO.riScContent,
                            )

                        if (!validationStatus.isValid) {
                            LOGGER.info("RiSc with id: ${riScContentResultDTO.riScId} failed validation")
                            return@map RiScContentResultDTO(
                                riScId = riScContentResultDTO.riScId,
                                status = ContentStatus.SchemaValidationFailed,
                                riScStatus = null,
                                riScContent = null,
                            )
                        }

                        LOGGER.info("RiSc with id: ${riScContentResultDTO.riScId} successfully validated")
                    }
                    riScContentResultDTO
                }.map { riScContentResultDTO ->
                    if (riScContentResultDTO.riScContent == null) return@map riScContentResultDTO
                    tryOrDefaultWithErrorLogging(
                        default =
                            RiScContentResultDTO(
                                riScId = riScContentResultDTO.riScId,
                                status = ContentStatus.Failure,
                                riScStatus = null,
                                riScContent = null,
                            ),
                        logger = LOGGER,
                    ) {
                        migrate(
                            riSc = RiSc.fromContent(riScContentResultDTO.riScContent),
                            lastPublished = riScContentResultDTO.lastPublished,
                            endVersion = latestSupportedVersion,
                        ).let { (migratedRiSc, migrationStatus) ->
                            riScContentResultDTO.copy(
                                riScContent = migratedRiSc.toJSON(),
                                migrationStatus = migrationStatus,
                            )
                        }
                    }
                }
        }

    /**
     * Converts the content response object to a RiScContentResult by decrypting it through the crypto service.
     *
     * @param riScId The ID of the RiSc the content belongs to.
     * @param riScStatus The status of the RiSc the content belongs to.
     * @param gcpAccessToken The GCP access token to decrypt the content with.
     * @param pullRequestUrl The URL to the open pull request for this RiSc, if any.
     * @param lastPublished Information about when the RiSc was last published, if already published.
     */
    private suspend fun GithubContentResponse.responseToRiScResult(
        riScId: String,
        riScStatus: RiScStatus,
        gcpAccessToken: GCPAccessToken,
        pullRequestUrl: String? = null,
        lastPublished: LastPublished? = null,
    ): RiScContentResultDTO =
        if (status == GithubStatus.Success) {
            try {
                val decryptedContent = cryptoService.decrypt(ciphertext = data(), gcpAccessToken = gcpAccessToken)
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
                RiScContentResultDTO(
                    riScId = riScId,
                    status = if (e is SOPSDecryptionException) ContentStatus.DecryptionFailed else ContentStatus.Failure,
                    riScStatus = riScStatus,
                    riScContent = null,
                )
            }
        } else {
            RiScContentResultDTO(
                riScId = riScId,
                status = if (status == GithubStatus.NotFound) ContentStatus.FileNotFound else ContentStatus.Failure,
                riScStatus = riScStatus,
                riScContent = null,
            )
        }

    /**
     * Updates the RiSc with the new content.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to push the RiSc to.
     * @param riScId The ID of the RiSc.
     * @param content The new content of the RiSc, including the SOPS config.
     * @param accessTokens The access tokens to use for authentication.
     * @param defaultBranch The name of the default branch of the repository.
     */
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

    /**
     * Creates a new RiSc.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to push the RiSc to.
     * @param content The new content of the RiSc, including the SOPS config.
     * @param accessTokens The access tokens to use for authentication.
     * @param defaultBranch The name of the default branch of the repository.
     * @param generateDefault Indicates if the content of the RiSc should be based on default RiSc types.
     * @param defaultRiScTypes Types of default RiScs to generate the RiSc from in cases where generateDefault is true
     * @throws CreatingRiScException If the creation fails.
     */
    suspend fun createRiSc(
        owner: String,
        repository: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
        defaultBranch: String,
        generateDefault: Boolean,
        defaultRiScTypes: List<DefaultRiScType>,
    ): CreateRiScResultDTO {
        val uniqueRiScId = generateRiScId(filenamePrefix)
        LOGGER.info("Generating default content")
        val riScContentWrapperObject =
            content.copy(
                riSc =
                    if (generateDefault) {
                        initRiScService.generateDefaultRiSc(content.riSc, defaultRiScTypes)
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

            if (result.status != ProcessingStatus.UpdatedRiSc) {
                throw CreatingRiScException(
                    message = "Failed to create RiSc with id $uniqueRiScId",
                    riScId = uniqueRiScId,
                )
            }

            return CreateRiScResultDTO(
                riScId = uniqueRiScId,
                status = ProcessingStatus.CreatedRiSc,
                statusMessage = "New RiSc was created",
                riScContent = riScContentWrapperObject.riSc,
                sopsConfig = riScContentWrapperObject.sopsConfig,
            )
        } catch (e: Exception) {
            throw CreatingRiScException(
                message = "${e.message} for risk scorecard with id $uniqueRiScId",
                riScId = uniqueRiScId,
            )
        }
    }

    /**
     * Updates or creates a RiSc with the provided content by encrypting the content and pushing it to a draft branch
     * for the RiSc on the provided repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to push the RiSc to.
     * @param riScId The ID of the RiSc.
     * @param content The new content of the RiSc.
     * @param sopsConfig The config to use for encryption/decryption of the RiSc.
     * @param accessTokens The access tokens to use for authentication.
     * @param defaultBranch The name of the default branch of the repository.
     */
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
        if (!validationStatus.isValid) {
            val validationError =
                validationStatus.details.joinToString("\n") { detail ->
                    detail.errors.values.joinToString("\n") { error -> "${detail.instanceLocation}: $error" }
                }
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
                    gitHubAccessToken = accessTokens.githubAccessToken,
                    userInfo = content.userInfo,
                    defaultBranch = defaultBranch,
                )

            if (riScApprovalPRStatus.pullRequest != null) {
                return PublishRiScResultDTO(
                    riScId = riScId,
                    status = ProcessingStatus.UpdatedRiScAndCreatedPullRequest,
                    statusMessage = "RiSc was updated and does not require approval - pull request was created",
                    pendingApproval = riScApprovalPRStatus.pullRequest.toPendingApprovalDTO(),
                )
            }

            return ProcessRiScResultDTO(
                riScId = riScId,
                status =
                    if (riScApprovalPRStatus.hasClosedPr) ProcessingStatus.UpdatedRiScRequiresNewApproval else ProcessingStatus.UpdatedRiSc,
                statusMessage =
                    "Risk scorecard was updated" +
                        if (riScApprovalPRStatus.hasClosedPr) " and has to be approved by a risk owner again" else "",
            )
        } catch (e: Exception) {
            throw UpdatingRiScException(
                message = "Failed with error ${e.message} for risk scorecard with id $riScId",
                riScId = riScId,
            )
        }
    }

    suspend fun deleteRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessTokens: AccessTokens,
    ): DeleteRiScResultDTO =
        githubConnector
            .deleteRiSc(
                owner = owner,
                repository = repository,
                riScId = riScId,
                accessToken = accessTokens.githubAccessToken.value,
            )

    /**
     * Prepares the provided RiSc for publication by creating a pull request for the drafted changes. The pull request
     * is made against the provided base branch.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to push the RiSc to.
     * @param riScId The ID of the RiSc.
     * @param gitHubAccessToken The access token to use for authorization against GitHub.
     * @param userInfo Information about the user that is creating the pull request, i.e., the user that approved the
     *                  changes.
     * @param baseBranch The name of the default branch of the repository.
     */
    suspend fun publishRiSc(
        owner: String,
        repository: String,
        riScId: String,
        gitHubAccessToken: String,
        userInfo: UserInfo,
    ): PublishRiScResultDTO {
        val pullRequestObject =
            githubConnector.createPullRequestForRiSc(
                owner = owner,
                repository = repository,
                riScId = riScId,
                requiresNewApproval = true,
                gitHubAccessToken = gitHubAccessToken,
                userInfo = userInfo,
            )

        return PublishRiScResultDTO(
            riScId = riScId,
            status = ProcessingStatus.CreatedPullRequest,
            statusMessage = "Pull request was created",
            pendingApproval = pullRequestObject.toPendingApprovalDTO(),
        )
    }

    suspend fun uploadRiScToRosa(
        riScId: String,
        repository: String,
        riSc: String,
    ): String? {
        val result = rosaClient.encryptAndUpload(riScId, repository, riSc)
        return result
    }

    suspend fun deleteRiscFromRosa(riScId: String) = rosaClient.deleteRiSc(riScId)

    /**
     * Converts the GitHub pull request object to a DTO for sending to the frontend.
     */
    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO =
        PendingApprovalDTO(
            pullRequestUrl = this.url,
            pullRequestName = this.head.ref,
        )
}
