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
    val status: ProcessingStatus,
    val statusMessage: String,
    val rosId: String,
)

data class ROSResultDTO(
    val status: ContentStatus,
    val rosStatus: ROSStatus,
    val rosContent: String?,
    val rosId: String,
)

class ROSIdentifiersResultDTO(
    val status: SimpleStatus,
    val rosIds: List<ROSIdentifier>,
)

class ROSPublishedObjectResultDTO(
    val pendingApproval: PendingApprovalDTO?,
    val rosId: String,
    val status: SimpleStatus,
)

class PendingApprovalDTO(
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
    UpdatedROS("Updated ROS successfully"),
    CreatedROS("Created new ROS successfully"),
    ErrorWhenUpdatingROS("Error when updating ROS"),
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
    ): List<ROSResultDTO> {
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
    ): ROSResultDTO {
        return when (this.status) {
            GithubStatus.Success ->
                try {
                    ROSResultDTO(ContentStatus.Success, rosStatus, this.decryptContent(), rosId)
                } catch (e: Exception) {
                    when (e) {
                        is SOPSDecryptionException ->
                            ROSResultDTO(
                                ContentStatus.DecryptionFailed,
                                rosStatus,
                                this.decryptContent(),
                                rosId,
                            )

                        else ->
                            ROSResultDTO(
                                ContentStatus.Failure,
                                rosStatus,
                                this.decryptContent(),
                                rosId,
                            )
                    }
                }

            GithubStatus.NotFound ->
                ROSResultDTO(
                    ContentStatus.FileNotFound,
                    rosStatus,
                    null,
                    rosId,
                )

            else -> ROSResultDTO(ContentStatus.Failure, rosStatus, this.decryptContent(), rosId)
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
                rosId = "ros-$uniqueROSId",
                content = content,
                accessToken = accessToken,
            )

        return when (result.status) {
            ProcessingStatus.UpdatedROS ->
                ProcessROSResultDTO(
                    ProcessingStatus.CreatedROS,
                    "Ny ROS ble opprettet",
                    uniqueROSId,
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
                ProcessingStatus.ROSNotValid,
                validationStatus.errors?.joinToString("\n") { it.error }.toString(),
                rosId,
            )
        }

        val encryptedData =
            try {
                SopsEncryptorForYaml.encrypt(content.ros, sopsEncryptorHelper)
            } catch (e: Exception) {
                return ProcessROSResultDTO(
                    ProcessingStatus.EncryptionFailed,
                    "Klarte ikke kryptere ROS",
                    rosId,
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

            return ProcessROSResultDTO(ProcessingStatus.UpdatedROS, "ROS ble oppdatert", rosId)
        } catch (e: Exception) {
            return ProcessROSResultDTO(
                ProcessingStatus.ErrorWhenUpdatingROS,
                "Feilet med feilemelding ${e.message} for ros med id $rosId",
                rosId,
            )
        }
    }

    fun fetchAllROSDraftsSentToPublication(
        owner: String,
        repository: String,
        accessToken: String,
    ): ROSIdentifiersResultDTO =
        ROSIdentifiersResultDTO(
            SimpleStatus.Success,
            githubConnector.fetchAllPullRequestsForROS(owner, repository, accessToken).toRosIdentifiersResultDTO(),
        )

    private fun List<GithubPullRequestObject>.toRosIdentifiersResultDTO(): List<ROSIdentifier> =
        this.map { ROSIdentifier(it.head.ref.split("/").last(), ROSStatus.Published) }

    fun publishROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): ROSPublishedObjectResultDTO {
        val pullRequestObject =
            githubConnector.createPullRequestForPublishingROS(
                owner,
                repository,
                rosId,
                accessToken,
            )

        return when (pullRequestObject != null) {
            true -> ROSPublishedObjectResultDTO(pullRequestObject.toPendingApprovalDTO(), rosId, SimpleStatus.Success)
            false -> ROSPublishedObjectResultDTO(null, rosId, SimpleStatus.Failure)
        }
    }

    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO =
        PendingApprovalDTO(
            pullRequestUrl = this.url,
            pullRequestName = this.head.ref,
        )
}
