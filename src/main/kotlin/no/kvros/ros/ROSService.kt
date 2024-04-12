package no.kvros.ros

import no.kvros.encryption.SOPS
import no.kvros.encryption.SOPSDecryptionException
import no.kvros.github.GithubConnector
import no.kvros.github.GithubContentResponse
import no.kvros.github.GithubPullRequestObject
import no.kvros.github.GithubStatus
import no.kvros.infra.connector.models.GCPAccessToken
import no.kvros.infra.connector.models.UserContext
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import no.kvros.infra.connector.JSONSchemaConnector

data class ProcessROSResultDTO(
    val rosId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            ProcessROSResultDTO(
                "",
                ProcessingStatus.InvalidUserContext,
                "Ugyldig ROS-resultat: ${ProcessingStatus.InvalidUserContext.message}",
            )
    }
}

data class ROSContentResultDTO(
    val rosId: String,
    val status: ContentStatus,
    val rosStatus: ROSStatus?,
    val rosContent: String?,
) {
    companion object {
        val INVALID_USER_CONTEXT =
            ROSContentResultDTO(
                rosId = "",
                status = ContentStatus.Failure,
                rosStatus = null,
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
    ROSNotValid("ROS is not valid according to JSON-Schema"),
    EncryptionFailed("Failed to encrypt ROS"),
    ErrorWhenUpdatingROS("Error when updating ROS"),
    CreatedROS("Created new ROS successfully"),
    UpdatedROS("Updated ROS successfully"),
    CreatedPullRequest("Created pull request for ROS"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidUserContext("Invalid user context"),
}

data class ROSIdentifier(
    val id: String,
    val status: ROSStatus,
)

enum class ROSStatus {
    Draft,
    SentForApproval,
    Published,
}

@Service
class ROSService(
    private val githubConnector: GithubConnector,
    private val JSONSchemaConnector: JSONSchemaConnector,
    @Value("\${sops.ageKey}") val ageKey: String,
) {
    fun fetchAllROSes(
        owner: String,
        repository: String,
        userContext: UserContext,
    ): List<ROSContentResultDTO> {
        val roses =
            githubConnector.fetchAllRosIdentifiersInRepository(
                owner,
                repository,
                userContext.githubAccessToken.value,
            ).let { ids ->
                ids.ids.map { identifier ->
                    when (identifier.status) {
                        ROSStatus.Published ->
                            githubConnector.fetchPublishedROS(
                                owner,
                                repository,
                                identifier.id,
                                userContext.githubAccessToken.value,
                            ).responseToRosResult(identifier.id, identifier.status, userContext.gcpAccessToken)

                        ROSStatus.SentForApproval,
                        ROSStatus.Draft,
                        ->
                            githubConnector.fetchDraftedROSContent(
                                owner,
                                repository,
                                identifier.id,
                                userContext.githubAccessToken.value,
                            ).responseToRosResult(identifier.id, identifier.status, userContext.gcpAccessToken)
                    }
                }
            }

        return roses
    }

    private fun GithubContentResponse.responseToRosResult(
        rosId: String,
        rosStatus: ROSStatus,
        gcpAccessToken: GCPAccessToken,
    ): ROSContentResultDTO {
        return when (status) {
            GithubStatus.Success ->
                try {
                    ROSContentResultDTO(rosId, ContentStatus.Success, rosStatus, decryptContent(gcpAccessToken))
                } catch (e: Exception) {
                    when (e) {
                        is SOPSDecryptionException ->
                            ROSContentResultDTO(
                                rosId,
                                ContentStatus.DecryptionFailed,
                                rosStatus,
                                decryptContent(
                                    gcpAccessToken,
                                ),
                            )

                        else ->
                            ROSContentResultDTO(rosId, ContentStatus.Failure, rosStatus, decryptContent(gcpAccessToken))
                    }
                }

            GithubStatus.NotFound ->
                ROSContentResultDTO(rosId, ContentStatus.FileNotFound, rosStatus, null)

            else ->
                ROSContentResultDTO(
                    rosId,
                    ContentStatus.Failure,
                    rosStatus,
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
        rosId: String,
        content: ROSWrapperObject,
        userContext: UserContext,
    ): ProcessROSResultDTO {
        return updateOrCreateROS(
            owner = owner,
            repository = repository,
            rosId = rosId,
            content = content,
            userContext = userContext,
        )
    }

    fun createROS(
        owner: String,
        repository: String,
        content: ROSWrapperObject,
        userContext: UserContext,
    ): ProcessROSResultDTO {
        val uniqueROSId = "ros-${RandomStringUtils.randomAlphanumeric(5)}"

        val result =
            updateOrCreateROS(
                owner = owner,
                repository = repository,
                rosId = uniqueROSId,
                content = content,
                userContext = userContext,
            )

        return when (result.status) {
            ProcessingStatus.UpdatedROS ->
                ProcessROSResultDTO(
                    uniqueROSId,
                    ProcessingStatus.CreatedROS,
                    "Ny ROS ble opprettet",
                )

            else -> result
        }
    }

    private fun updateOrCreateROS(
        owner: String,
        repository: String,
        rosId: String,
        content: ROSWrapperObject,
        userContext: UserContext,
    ): ProcessROSResultDTO {
        val jsonSchema =
            JSONSchemaConnector.fetchJSONSchema(content.schemaVersion.replace('.', '_'))
                ?: return ProcessROSResultDTO(
                    rosId,
                    ProcessingStatus.ErrorWhenUpdatingROS,
                    "Kunne ikke hente JSON Schema",
                )

        val validationStatus = JSONValidator.validateJSON(jsonSchema, content.ros)
        if (!validationStatus.valid) {
            return ProcessROSResultDTO(
                rosId,
                ProcessingStatus.ROSNotValid,
                validationStatus.errors?.joinToString("\n") { it.error }.toString(),
            )
        }

        val sopsConfig =
            githubConnector.fetchSopsConfig(owner, repository, userContext.githubAccessToken)
                ?: return ProcessROSResultDTO(
                    rosId,
                    ProcessingStatus.ErrorWhenUpdatingROS,
                    "Kunne ikke hente sops-config",
                )

        val encryptedData =
            try {
                SOPS.encrypt(content.ros, sopsConfig, userContext.gcpAccessToken)
            } catch (e: Exception) {
                return ProcessROSResultDTO(
                    rosId,
                    ProcessingStatus.EncryptionFailed,
                    "Klarte ikke kryptere ROS",
                )
            }

        try {
            val hasClosedPR =
                githubConnector.updateOrCreateDraft(
                    owner = owner,
                    repository = repository,
                    rosId = rosId,
                    fileContent = encryptedData,
                    requiresNewApproval = content.isRequiresNewApproval,
                    userContext = userContext,
                )

            return ProcessROSResultDTO(
                rosId,
                ProcessingStatus.UpdatedROS,
                "ROS ble oppdatert" + if (hasClosedPR) " og må godkjennes av risikoeier på nytt" else "",
            )
        } catch (e: Exception) {
            return ProcessROSResultDTO(
                rosId,
                ProcessingStatus.ErrorWhenUpdatingROS,
                "Feilet med feilmelding ${e.message} for ros med id $rosId",
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
