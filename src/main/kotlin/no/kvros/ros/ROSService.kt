package no.kvros.ros

import no.kvros.encryption.SOPSDecryptionException
import no.kvros.encryption.SopsEncryptionKeyProvider
import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.encryption.SopsEncryptorHelper
import no.kvros.encryption.SopsProviderAndCredentials
import no.kvros.github.GithubPullRequestObject
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

data class ProcessROSResultDTO(
    val rosId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
)

data class ROSContentResultDTO(
    val rosId: String,
    val status: ContentStatus,
    val rosStatus: ROSStatus,
    val rosContent: String?,
)

data class PublishROSResultDTO(
    val rosId: String,
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

enum class SimpleStatus {
    Success,
    Failure,
}

enum class ProcessingStatus(val message: String) {
    ROSNotValid("ROS is not valid according to JSON-Schema"),
    EncryptionFailed("Failed to encrypt ROS"),
    CouldNotCreateBranch("Could not create new branch for ROS"),
    ErrorWhenUpdatingROS("Error when updating ROS"),
    CreatedROS("Created new ROS successfully"),
    UpdatedROS("Updated ROS successfully"),
    CreatedPullRequest("Created pull request for ROS"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
}

data class ROSIdentifier(
    val id: String,
    val status: ROSStatus,
)

enum class ROSStatus(val description: String) {
    Draft("Kladd"),
    SentForApproval("Sendt til godkjenning"),
    Published("Publisert"),
}

@Service
class ROSService(
    private val githubConnector: GithubConnector,
    @Value("\${sops.rosKeyResourcePath}")
    private val gcpKeyResourcePath: String,
    @Value("\${sops.agePublicKey}")
    private val agePublicKey: String,
) {
    private val sopsEncryptorHelper =
        SopsEncryptorHelper(
            sopsProvidersAndCredentials =
                listOf(
                    SopsProviderAndCredentials(
                        provider = SopsEncryptionKeyProvider.GoogleCloudPlatform,
                        publicKeyOrPath = gcpKeyResourcePath,
                    ),
                    SopsProviderAndCredentials(
                        provider = SopsEncryptionKeyProvider.AGE,
                        publicKeyOrPath = agePublicKey,
                    ),
                ),
        )

    fun fetchAllROSes(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSContentResultDTO> {
        val roses =
            githubConnector.fetchAllRosIdentifiersInRepository(owner, repository, accessToken).let { ids ->
                ids.ids.map { identifier ->
                    when (identifier.status) {
                        ROSStatus.Published ->
                            githubConnector.fetchPublishedROS(
                                owner,
                                repository,
                                identifier.id,
                                accessToken,
                            ).responseToRosResult(identifier.id, identifier.status)

                        ROSStatus.SentForApproval,
                        ROSStatus.Draft,
                        ->
                            githubConnector.fetchDraftedROSContent(
                                owner,
                                repository,
                                identifier.id,
                                accessToken,
                            ).responseToRosResult(identifier.id, identifier.status)
                    }
                }
            }

        return roses
    }

    private fun GithubContentResponse.responseToRosResult(
        rosId: String,
        rosStatus: ROSStatus,
    ): ROSContentResultDTO {
        return when (status) {
            GithubStatus.Success ->
                try {
                    ROSContentResultDTO(rosId, ContentStatus.Success, rosStatus, decryptContent())
                } catch (e: Exception) {
                    when (e) {
                        is SOPSDecryptionException ->
                            ROSContentResultDTO(rosId, ContentStatus.DecryptionFailed, rosStatus, decryptContent())

                        else ->
                            ROSContentResultDTO(rosId, ContentStatus.Failure, rosStatus, decryptContent())
                    }
                }

            GithubStatus.NotFound ->
                ROSContentResultDTO(rosId, ContentStatus.FileNotFound, rosStatus, null)

            else -> ROSContentResultDTO(rosId, ContentStatus.Failure, rosStatus, decryptContent())
        }
    }

    fun GithubContentResponse.decryptContent(): String =
        this.data()
            .let { Base64.getMimeDecoder().decode(it).decodeToString() }
            .let { SopsEncryptorForYaml.decrypt(ciphertext = it, sopsEncryptorHelper) }

    fun updateROS(
        owner: String,
        repository: String,
        rosId: String,
        content: ROSWrapperObject,
        accessToken: String,
    ): ProcessROSResultDTO {
        return updateOrCreateROS(
            owner = owner,
            repository = repository,
            rosId = rosId,
            content = content,
            accessToken = accessToken,
        )
    }

    fun createROS(
        owner: String,
        repository: String,
        content: ROSWrapperObject,
        accessToken: String,
    ): ProcessROSResultDTO {
        val uniqueROSId = "ros-${RandomStringUtils.randomAlphanumeric(5)}"

        val result =
            updateOrCreateROS(
                owner = owner,
                repository = repository,
                rosId = uniqueROSId,
                content = content,
                accessToken = accessToken,
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
        accessToken: String,
    ): ProcessROSResultDTO {
        val validationStatus = JSONValidator.validateJSON(content.ros)
        if (!validationStatus.valid) {
            return ProcessROSResultDTO(
                rosId,
                ProcessingStatus.ROSNotValid,
                validationStatus.errors?.joinToString("\n") { it.error }.toString(),
            )
        }

        val encryptedData =
            try {
                SopsEncryptorForYaml.encrypt(content.ros, sopsEncryptorHelper)
            } catch (e: Exception) {
                return ProcessROSResultDTO(
                    rosId,
                    ProcessingStatus.EncryptionFailed,
                    "Klarte ikke kryptere ROS",
                )
            }

        try {
            githubConnector.updateOrCreateDraft(
                owner = owner,
                repository = repository,
                rosId = rosId,
                fileContent = encryptedData,
                accessToken = accessToken,
            )

            return ProcessROSResultDTO(rosId, ProcessingStatus.UpdatedROS, "ROS ble oppdatert")
        } catch (e: Exception) {
            return ProcessROSResultDTO(
                rosId,
                ProcessingStatus.ErrorWhenUpdatingROS,
                "Feilet med feilemelding ${e.message} for ros med id $rosId",
            )
        }
    }

    private fun List<GithubPullRequestObject>.toRosIdentifiersResultDTO(): List<ROSIdentifier> =
        this.map { ROSIdentifier(it.head.ref.split("/").last(), ROSStatus.Published) }

    fun publishROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): PublishROSResultDTO {
        val pullRequestObject =
            githubConnector.createPullRequestForPublishingROS(
                owner,
                repository,
                rosId,
                accessToken,
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
