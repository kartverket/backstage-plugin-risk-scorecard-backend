package no.risc.risc

import no.risc.encryption.SOPS
import no.risc.github.GithubConnector
import no.risc.github.GithubContentResponse
import no.risc.github.GithubPullRequestObject
import no.risc.github.GithubStatus
import no.risc.infra.connector.JSONSchemaConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import no.risc.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

data class ProcessRiScResultDTO(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            ProcessRiScResultDTO(
                "",
                ProcessingStatus.InvalidUserContext,
                "Invalid RiSc result: ${ProcessingStatus.InvalidUserContext.message}",
            )
    }
}

data class RiScContentResultDTO(
    val riScId: String,
    val status: ContentStatus,
    val riScStatus: RiScStatus?,
    val riScContent: String?,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            RiScContentResultDTO(
                riScId = "",
                status = ContentStatus.Failure,
                riScStatus = null,
                riScContent = "",
            )
    }
}

data class PublishRiScResultDTO(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
    val pendingApproval: PendingApprovalDTO?,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            PublishRiScResultDTO(
                "",
                ProcessingStatus.InvalidUserContext,
                "Invalid RiSc result: ${ProcessingStatus.InvalidUserContext.message}",
                null,
            )
    }
}

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
    RiScNotValid("RiSc is not valid according to JSON-Schema"),
    EncryptionFailed("Failed to encrypt RiSc"),
    ErrorWhenUpdatingRiSc("Error when updating RiSc"),
    CreatedRiSc("Created new RiSc successfully"),
    UpdatedRiSc("Updated RiSc successfully"),
    CreatedPullRequest("Created pull request for RiSc"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidUserContext("Invalid user context"),
}

data class RiScIdentifier(
    val id: String,
    val status: RiScStatus,
)

enum class RiScStatus {
    Draft,
    SentForApproval,
    Published,
}

@Service
class RiScService(
    private val githubConnector: GithubConnector,
    private val JSONSchemaConnector: JSONSchemaConnector,
    @Value("\${sops.ageKey}") val ageKey: String,
    @Value("\${filename.prefix}") val filenamePrefix: String,
) {
    fun fetchAllRiScs(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
    ): List<RiScContentResultDTO> {
        val riScs =
            githubConnector.fetchAllRiScIdentifiersInRepository(
                owner,
                repository,
                accessTokens.githubAccessToken.value,
            ).let { ids ->
                ids.ids.map { identifier ->
                    when (identifier.status) {
                        RiScStatus.Published ->
                            githubConnector.fetchPublishedRiSc(
                                owner,
                                repository,
                                identifier.id,
                                accessTokens.githubAccessToken.value,
                            ).responseToRiScResult(identifier.id, identifier.status, accessTokens.gcpAccessToken)

                        RiScStatus.SentForApproval,
                        RiScStatus.Draft,
                        ->
                            githubConnector.fetchDraftedRiScContent(
                                owner,
                                repository,
                                identifier.id,
                                accessTokens.githubAccessToken.value,
                            ).responseToRiScResult(identifier.id, identifier.status, accessTokens.gcpAccessToken)
                    }
                }
            }

        return riScs
    }

    private fun GithubContentResponse.responseToRiScResult(
        riScId: String,
        riScStatus: RiScStatus,
        gcpAccessToken: GCPAccessToken,
    ): RiScContentResultDTO {
        return when (status) {
            GithubStatus.Success ->
                try {
                    RiScContentResultDTO(riScId, ContentStatus.Success, riScStatus, decryptContent(gcpAccessToken))
                } catch (e: Exception) {
                    when (e) {
                        is no.risc.encryption.SOPSDecryptionException ->
                            RiScContentResultDTO(
                                riScId,
                                ContentStatus.DecryptionFailed,
                                riScStatus,
                                decryptContent(
                                    gcpAccessToken,
                                ),
                            )

                        else ->
                            RiScContentResultDTO(riScId, ContentStatus.Failure, riScStatus, decryptContent(gcpAccessToken))
                    }
                }

            GithubStatus.NotFound ->
                RiScContentResultDTO(riScId, ContentStatus.FileNotFound, riScStatus, null)

            else ->
                RiScContentResultDTO(
                    riScId,
                    ContentStatus.Failure,
                    riScStatus,
                    decryptContent(gcpAccessToken),
                )
        }
    }

    fun GithubContentResponse.decryptContent(gcpAccessToken: GCPAccessToken): String =
        this.data()
            .let { Base64.getMimeDecoder().decode(it).decodeToString() }
            .let {
                SOPS.decrypt(
                    ciphertext = it,
                    gcpAccessToken = gcpAccessToken,
                    agePrivateKey = ageKey,
                )
            }

    fun updateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        return updateOrCreateRiSc(
            owner = owner,
            repository = repository,
            riScId = riScId,
            content = content,
            accessTokens = accessTokens,
        )
    }

    fun createRiSc(
        owner: String,
        repository: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
        userInfo: UserInfo,
    ): ProcessRiScResultDTO {
        val uniqueRiScId = "$filenamePrefix-${RandomStringUtils.randomAlphanumeric(5)}"

        val result =
            updateOrCreateRiSc(
                owner = owner,
                repository = repository,
                riScId = uniqueRiScId,
                content = content,
                accessTokens = accessTokens,
            )

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

    private fun updateOrCreateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        val jsonSchema =
            JSONSchemaConnector.fetchJSONSchema(content.schemaVersion.replace('.', '_'))
                ?: return ProcessRiScResultDTO(
                    riScId,
                    ProcessingStatus.ErrorWhenUpdatingRiSc,
                    "Could not fetch JSON Schema",
                )

        val validationStatus = JSONValidator.validateJSON(jsonSchema, content.riSc)
        if (!validationStatus.valid) {
            return ProcessRiScResultDTO(
                riScId,
                ProcessingStatus.RiScNotValid,
                validationStatus.errors?.joinToString("\n") { it.error }.toString(),
            )
        }

        val sopsConfig =
            githubConnector.fetchSopsConfig(owner, repository, accessTokens.githubAccessToken)
                ?: return ProcessRiScResultDTO(
                    riScId,
                    ProcessingStatus.ErrorWhenUpdatingRiSc,
                    "Could not fetch SOPS config",
                )

        val encryptedData =
            try {
                SOPS.encrypt(content.riSc, sopsConfig, accessTokens.gcpAccessToken)
            } catch (e: Exception) {
                return ProcessRiScResultDTO(
                    riScId,
                    ProcessingStatus.EncryptionFailed,
                    "Could not encrypt RiSc",
                )
            }

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
                "RiSc was updated" + if (hasClosedPR) " and has to be approved by av risk owner again" else "",
            )
        } catch (e: Exception) {
            return ProcessRiScResultDTO(
                riScId,
                ProcessingStatus.ErrorWhenUpdatingRiSc,
                "Failed with with error ${e.message} for RiSc with id $riScId",
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
            githubConnector.createPullRequestForPublishingRiSc(
                owner = owner,
                repository = repository,
                riScId = riScId,
                requiresNewApproval = true,
                accessTokens = accessTokens,
                userInfo = userInfo,
            )

        return when (pullRequestObject) {
            null ->
                PublishRiScResultDTO(
                    riScId,
                    ProcessingStatus.ErrorWhenCreatingPullRequest,
                    "Could not create pull request",
                    null,
                )

            else ->
                PublishRiScResultDTO(
                    riScId,
                    ProcessingStatus.CreatedPullRequest,
                    "Pull request was created",
                    pullRequestObject.toPendingApprovalDTO(),
                )
        }
    }

    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO =
        PendingApprovalDTO(
            pullRequestUrl = this.url,
            pullRequestName = this.head.ref,
        )

    fun fetchLatestJSONSchema(): GithubContentResponse {
        return githubConnector.fetchJSONSchemas()
    }
}
