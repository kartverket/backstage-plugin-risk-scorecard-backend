package no.kvros.ros

import no.kvros.encryption.*
import no.kvros.github.GithubPullRequestObject
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*


class ProcessROSResultDTO(
    val status: ProcessingStatus,
    val statusMessage: String,
    val rosId: String?,
    val rosContent: String?
)

class ROSContentResultDTO(
    val status: ContentStatus,
    val rosContent: String?,
    val rosId: String
)

class ROSIdentifiersResultDTO(
    val status: SimpleStatus,
    val rosIds: List<ROSIdentifier>,
)

class ROSPublishedObjectResultDTO(
    val pendingApproval: PendingApprovalDTO?,
    val rosId: String,
    val status: SimpleStatus
)

class PendingApprovalDTO(
    val pullRequestUrl: String,
    val pullRequestName: String,
)

enum class ContentStatus {
    Success,
    FileNotFound,
    DecryptionFailed,
    Failure
}

enum class SimpleStatus {
    Success,
    Failure
}

enum class ProcessingStatus(val message: String) {
    ROSNotValid("ROS is not valid according to JSON-Schema"),
    EncrptionFailed("Kryptering av ROS feilet"),
    CouldNotCreateBranch("Could not create new branch for ROS"),
    UpdatedROS("Created new branch for new ROS"),
    ErrorWhenUpdatingROS("Error when updating ROS"),
}

data class ROSIdentifier(
    val id: String,
    val status: ROSStatus
)

enum class ROSStatus(val description: String) {
    Draft("Kladd"),
    SentForApproval("Sendt til godkjenning"),
    Published("Publisert")
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

    fun fetchROSFilenames(
        owner: String,
        repository: String,
        accessToken: String,
    ): ROSIdentifiersResultDTO {
        val githubResponse = githubConnector.fetchAllRosIdentifiersInRepository(owner, repository, accessToken)

        return when (githubResponse.status) {
            GithubStatus.Success -> ROSIdentifiersResultDTO(SimpleStatus.Success, githubResponse.ids)
            else -> ROSIdentifiersResultDTO(SimpleStatus.Failure, emptyList())
        }
    }

    fun fetchROSContent(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): ROSContentResultDTO {
        val draftedROS = fetchDraftedROS(owner, repository, rosId, accessToken)
        if (draftedROS.status == ContentStatus.FileNotFound) return fetchPublishedROS(
            owner,
            repository,
            rosId,
            accessToken
        )

        return draftedROS
    }

    private fun fetchDraftedROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ): ROSContentResultDTO =
        githubConnector.fetchDraftedROSContent(owner, repository, rosId, accessToken).responseToRosResult(rosId)

    fun fetchPublishedROS(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): ROSContentResultDTO =
        githubConnector.fetchPublishedROS(owner, repository, id, accessToken).responseToRosResult(id)

    private fun GithubContentResponse.responseToRosResult(
        rosId: String
    ): ROSContentResultDTO {
        return when (this.status) {
            GithubStatus.Success -> try {
                ROSContentResultDTO(ContentStatus.Success, this.decryptContent(), rosId)
            } catch (e: Exception) {
                when (e) {
                    is SOPSDecryptionException -> ROSContentResultDTO(
                        ContentStatus.DecryptionFailed,
                        this.decryptContent(),
                        rosId
                    )

                    else -> ROSContentResultDTO(ContentStatus.Failure, this.decryptContent(), rosId)
                }
            }

            GithubStatus.NotFound -> ROSContentResultDTO(
                ContentStatus.FileNotFound,
                null,
                rosId
            )

            else -> ROSContentResultDTO(ContentStatus.Failure, this.decryptContent(), rosId)
        }
    }

    fun GithubContentResponse.decryptContent(): String = this.data()
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
            accessToken = accessToken
        )
    }

    fun createROS(
        owner: String,
        repository: String,
        content: ROSWrapperObject,
        accessToken: String,
    ): ProcessROSResultDTO {
        val uniqueROSId = RandomStringUtils.randomAlphanumeric(5)
        return updateOrCreateROS(
            owner = owner,
            repository = repository,
            rosId = "ros-$uniqueROSId",
            content = content,
            accessToken = accessToken
        )
    }

    private fun updateOrCreateROS(
        owner: String,
        repository: String,
        rosId: String,
        content: ROSWrapperObject,
        accessToken: String,
    ): ProcessROSResultDTO {
        val validationStatus = JSONValidator.validateJSON(content.ros)
        if (!validationStatus.valid)
            return ProcessROSResultDTO(
                status = ProcessingStatus.ROSNotValid,
                statusMessage = validationStatus.errors?.joinToString("\n") { it.error }.toString(),
                rosId = rosId,
                rosContent = content.ros
            )

        val encryptedData =
            try {
                SopsEncryptorForYaml.encrypt(content.ros, sopsEncryptorHelper)
            } catch (_: Exception) {
                return ProcessROSResultDTO(
                    status = ProcessingStatus.EncrptionFailed,
                    statusMessage = "Klarte ikke kryptere ROS",
                    rosId = rosId,
                    rosContent = content.ros
                )
            }

        try {
            githubConnector.updateOrCreateDraft(
                owner = owner,
                repository = repository,
                rosId = rosId,
                fileContent = encryptedData,
                accessToken = accessToken
            )

            return ProcessROSResultDTO(ProcessingStatus.UpdatedROS, "ROS ble oppdatert", rosId, content.ros)
        } catch (e: Exception) {
            return ProcessROSResultDTO(
                status = ProcessingStatus.ErrorWhenUpdatingROS,
                statusMessage = "Kunne ikke oppdatere ROS med id $rosId",
                rosId = rosId,
                rosContent = content.ros
            )
        }
    }

    fun fetchAllROSDraftsSentToPublication(
        owner: String,
        repository: String,
        accessToken: String
    ): ROSIdentifiersResultDTO =
        ROSIdentifiersResultDTO(
            SimpleStatus.Success,
            githubConnector.fetchAllPullRequestsForROS(owner, repository, accessToken).toRosIdentifiersResultDTO()
        )

    private fun List<GithubPullRequestObject>.toRosIdentifiersResultDTO(): List<ROSIdentifier> =
        this.map { ROSIdentifier(it.head.ref.split("/").last(), ROSStatus.Published) }

    fun publishROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ): ROSPublishedObjectResultDTO {
        val pullRequestObject = githubConnector.createPullRequestForPublishingROS(
            owner,
            repository,
            rosId,
            accessToken
        )

        return when (pullRequestObject != null) {
            true -> ROSPublishedObjectResultDTO(pullRequestObject.toPendingApprovalDTO(), rosId, SimpleStatus.Success)
            false -> ROSPublishedObjectResultDTO(null, rosId, SimpleStatus.Failure)
        }
    }

    private fun GithubPullRequestObject.toPendingApprovalDTO(): PendingApprovalDTO = PendingApprovalDTO(
        pullRequestUrl = this.url,
        pullRequestName = this.head.ref
    )

}
