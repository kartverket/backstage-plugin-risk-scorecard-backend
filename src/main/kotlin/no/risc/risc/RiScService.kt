package no.risc.risc

import java.util.Base64
import no.risc.encryption.SOPS
import no.risc.github.GithubConnector
import no.risc.github.GithubContentResponse
import no.risc.github.GithubPullRequestObject
import no.risc.github.GithubStatus
import no.risc.infra.connector.JSONSchemaConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.UserContext
import no.risc.risc.models.RiScWrapperObject
import no.risc.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

data class ProcessRiScResultDTO(
    val rosId: String,
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
    val rosContent: String?,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            RiScContentResultDTO(
                riScId = "",
                status = ContentStatus.Failure,
                riScStatus = null,
                rosContent = "",
            )
    }
}

data class PublishROSResultDTO(
    val rosId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
    val pendingApproval: PendingApprovalDTO?,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            PublishROSResultDTO(
                "",
                ProcessingStatus.InvalidUserContext,
                "Ugyldig ROS-resultat: ${ProcessingStatus.InvalidUserContext.message}",
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
    ErrorWhenUpdatingROS("Error when updating RiSc"),
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
        userContext: UserContext,
    ): List<RiScContentResultDTO> {
        val riScs =
            githubConnector.fetchAllRiScIdentifiersInRepository(
                owner,
                repository,
                userContext.githubAccessToken.value,
            ).let { ids ->
                ids.ids.map { identifier ->
                    when (identifier.status) {
                        RiScStatus.Published ->
                            githubConnector.fetchPublishedRiSc(
                                owner,
                                repository,
                                identifier.id,
                                userContext.githubAccessToken.value,
                            ).responseToRiScResult(identifier.id, identifier.status, userContext.gcpAccessToken)

                        RiScStatus.SentForApproval,
                        RiScStatus.Draft,
                                             ->
                            githubConnector.fetchDraftedRiScContent(
                                owner,
                                repository,
                                identifier.id,
                                userContext.githubAccessToken.value,
                            ).responseToRiScResult(identifier.id, identifier.status, userContext.gcpAccessToken)
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

    fun updateROS(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        userContext: UserContext,
    ): ProcessRiScResultDTO {
        return updateOrCreateROS(
            owner = owner,
            repository = repository,
            riScId = riScId,
            content = content,
            userContext = userContext,
        )
    }

    fun createROS(
        owner: String,
        repository: String,
        content: RiScWrapperObject,
        userContext: UserContext,
    ): ProcessRiScResultDTO {
        val uniqueRiScId = "${filenamePrefix}-${RandomStringUtils.randomAlphanumeric(5)}"

        val result =
            updateOrCreateROS(
                owner = owner,
                repository = repository,
                riScId = uniqueRiScId,
                content = content,
                userContext = userContext,
            )

        return when (result.status) {
            ProcessingStatus.UpdatedRiSc ->
                ProcessRiScResultDTO(
                    uniqueRiScId,
                    ProcessingStatus.CreatedRiSc,
                    "Ny ROS ble opprettet",
                )

            else -> result
        }
    }

    private fun updateOrCreateROS(
        owner: String,
        repository: String,
        riScId: String,
        content: RiScWrapperObject,
        userContext: UserContext,
    ): ProcessRiScResultDTO {
        val jsonSchema =
            JSONSchemaConnector.fetchJSONSchema(content.schemaVersion.replace('.', '_'))
                ?: return ProcessRiScResultDTO(
                    riScId,
                    ProcessingStatus.ErrorWhenUpdatingROS,
                    "Kunne ikke hente JSON Schema",
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
            githubConnector.fetchSopsConfig(owner, repository, userContext.githubAccessToken)
                ?: return ProcessRiScResultDTO(
                    riScId,
                    ProcessingStatus.ErrorWhenUpdatingROS,
                    "Kunne ikke hente sops-config",
                )

        val encryptedData =
            try {
                SOPS.encrypt(content.riSc, sopsConfig, userContext.gcpAccessToken)
            } catch (e: Exception) {
                return ProcessRiScResultDTO(
                    riScId,
                    ProcessingStatus.EncryptionFailed,
                    "Klarte ikke kryptere ROS",
                )
            }

        try {
            val hasClosedPR =
                githubConnector.updateOrCreateDraft(
                    owner = owner,
                    repository = repository,
                    rosId = riScId,
                    fileContent = encryptedData,
                    requiresNewApproval = content.isRequiresNewApproval,
                    userContext = userContext,
                )

            return ProcessRiScResultDTO(
                riScId,
                ProcessingStatus.UpdatedRiSc,
                "ROS ble oppdatert" + if (hasClosedPR) " og må godkjennes av risikoeier på nytt" else "",
            )
        } catch (e: Exception) {
            return ProcessRiScResultDTO(
                riScId,
                ProcessingStatus.ErrorWhenUpdatingROS,
                "Feilet med feilmelding ${e.message} for ros med id $riScId",
            )
        }
    }

    fun publishROS(
        owner: String,
        repository: String,
        rosId: String,
        userContext: UserContext,
    ): PublishROSResultDTO {
        val pullRequestObject =
            githubConnector.createPullRequestForPublishingROS(
                owner,
                repository,
                rosId,
                requiresNewApproval = true,
                userContext,
            )

        return when (pullRequestObject) {
            null ->
                PublishROSResultDTO(
                    rosId,
                    ProcessingStatus.ErrorWhenCreatingPullRequest,
                    "Kunne ikke opprette pull request",
                    null,
                )

            else ->
                PublishROSResultDTO(
                    rosId,
                    ProcessingStatus.CreatedPullRequest,
                    "Pull request ble opprettet",
                    pullRequestObject.toPendingApprovalDTO(),
                )
        }
    }

    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO =
        PendingApprovalDTO(
            pullRequestUrl = this.url,
            pullRequestName = this.head.ref,
        )
}
