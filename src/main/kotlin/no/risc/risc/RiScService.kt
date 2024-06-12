package no.risc.risc

import no.risc.encryption.SOPS
import no.risc.encryption.SOPSDecryptionException
import no.risc.exception.exceptions.*
import no.risc.github.GithubConnector
import no.risc.github.GithubContentResponse
import no.risc.github.GithubPullRequestObject
import no.risc.github.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import no.risc.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

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

data class RiScContentResultDTO(
    val riScId: String,
    val status: ContentStatus,
    val riScStatus: RiScStatus?,
    val riScContent: String?,
) {
    companion object {
        val INVALID_ACCESS_TOKENS =
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
        val INVALID_ACCESS_TOKENS =
            PublishRiScResultDTO(
                "",
                ProcessingStatus.InvalidAccessTokens,
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
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
    RiScNotValid("Risk scorecard is not valid according to JSON Schema"),
    EncryptionFailed("Failed to encrypt risk scorecard"),
    ErrorWhenUpdatingRiSc("Error when updating risk scorecard"),
    CreatedRiSc("Created new risk scorecard successfully"),
    UpdatedRiSc("Updated risk scorecard successfully"),
    CreatedPullRequest("Created pull request for risk scorecard"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidAccessTokens("Invalid access tokens"),
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
    @Value("\${sops.ageKey}") val ageKey: String,
    @Value("\${filename.prefix}") val filenamePrefix: String,
    @Value("\${filename.postfix}") val filenamePostfix: String,
) {
    fun fetchAllRiScs(
        owner: String,
        repository: String,
        accessTokens: AccessTokens,
    ): List<RiScContentResultDTO> =
        githubConnector.fetchAllRiScIdentifiersInRepository(
            owner,
            repository,
            accessTokens.githubAccessToken.value,
        ).ids.map { id ->
            val fetchRisc =
                when (id.status) {
                    RiScStatus.Published -> githubConnector::fetchPublishedRiSc
                    RiScStatus.SentForApproval, RiScStatus.Draft -> githubConnector::fetchDraftedRiScContent
                }
            fetchRisc(owner, repository, id.id, accessTokens.githubAccessToken.value)
                .responseToRiScResult(id.id, id.status, accessTokens.gcpAccessToken)
        }

    private fun GithubContentResponse.responseToRiScResult(
        riScId: String,
        riScStatus: RiScStatus,
        gcpAccessToken: GCPAccessToken,
    ): RiScContentResultDTO =
        when (status) {
            GithubStatus.Success ->
                try {
                    RiScContentResultDTO(riScId, ContentStatus.Success, riScStatus, decryptContent(gcpAccessToken))
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

    private fun GithubContentResponse.decryptContent(gcpAccessToken: GCPAccessToken): String =
        SOPS.decrypt(
            ciphertext = data(),
            gcpAccessToken = gcpAccessToken,
            agePrivateKey = ageKey,
        )

    fun updateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO = updateOrCreateRiSc(owner, repository, riScId, content, accessTokens)

    fun createRiSc(
        owner: String,
        repository: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        val uniqueRiScId = RandomStringUtils.randomAlphanumeric(5)
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

    private fun updateOrCreateRiSc(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        accessTokens: AccessTokens,
    ): ProcessRiScResultDTO {
        val jsonSchema = githubConnector.fetchJSONSchema("risc_schema_en_v${content.schemaVersion.replace('.', '_')}.json")
        if (jsonSchema.status != GithubStatus.Success) {
            throw JSONSchemaFetchException(
                message = "Failed when fetching JSON schema from Github with status: ${jsonSchema.status}, and error message: ${jsonSchema.data}",
                riScId = riScId
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
                responseMessage = "Could not fetch SOPS config"
            )
        }

        val encryptedData = SOPS.encrypt(content.riSc, sopsConfig.data(), accessTokens.gcpAccessToken, riScId)

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
