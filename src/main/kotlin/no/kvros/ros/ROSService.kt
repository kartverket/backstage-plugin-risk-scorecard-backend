package no.kvros.ros

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
import java.util.*


abstract class ROSResult(
    val status: ProcessingStatus,
    val statusMessage: String
)

class PostROSResult(status: ProcessingStatus, statusMessage: String) : ROSResult(status, statusMessage)
class GetROSResult(
    status: ProcessingStatus,
    statusMessage: String,
    val rosContent: String,
    val rosId: String
) :
    ROSResult(status, statusMessage)

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
    private val draftPostfix = " (kladd)"

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

    fun fetchROSContent(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): String? =
        fetchDraftedROS(owner, repository, rosId, accessToken) ?: fetchPublishedROS(
            owner,
            repository,
            rosId,
            accessToken
        )

    private fun fetchDraftedROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ): String? =
        githubConnector.fetchDraftedROSContent(owner, repository, rosId, accessToken)
            ?.let { Base64.getMimeDecoder().decode(it).decodeToString() }
            ?.let { SopsEncryptorForYaml.decrypt(ciphertext = it, sopsEncryptorHelper) }

    fun fetchPublishedROS(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): String? =
        githubConnector
            .fetchPublishedROS(owner, repository, id, accessToken)
            ?.let { Base64.getMimeDecoder().decode(it).decodeToString() }
            ?.let { SopsEncryptorForYaml.decrypt(ciphertext = it, sopsEncryptorHelper) }

    fun fetchROSFilenames(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> {
        val draftROSes = try {
            githubConnector.fetchAllROSBranches(owner, repository, accessToken)
        } catch (e: Exception) {
            emptyList()
        }

        val publishedROSes = try {
            githubConnector.fetchPublishedROSIdentifiers(
                owner,
                repository,
                accessToken
            )
        } catch (e: Exception) {
            emptyList()
        }

        val rosSentForApproval = try {
            githubConnector.fetchROSIdentifiersSentForApproval(owner, repository, accessToken)
        } catch (e: Exception) {
            emptyList()
        }


        return combinePublishedDraftAndSentForApproval(draftROSes, publishedROSes, rosSentForApproval)
    }

    fun combinePublishedDraftAndSentForApproval(
        draftRosList: List<ROSIdentifier>,
        sentForApprovalList: List<ROSIdentifier>,
        publishedRosList: List<ROSIdentifier>
    ): List<ROSIdentifier> {
        val draftIds = draftRosList.map { it.id }
        val sentForApprovalsIds = sentForApprovalList.map { it.id }
        val publisedROSIdentifiersNotInDraftList =
            publishedRosList.filter { it.id !in draftIds && it.id !in sentForApprovalsIds }
        val draftROSIdentifiersNotInSentForApprovalsList = draftRosList.filter { it.id !in sentForApprovalsIds }

        return sentForApprovalList + publisedROSIdentifiersNotInDraftList + draftROSIdentifiersNotInSentForApprovalsList
    }

    fun updateROS(
        owner: String,
        repository: String,
        rosId: String,
        content: ROSWrapperObject,
        accessToken: String,
    ): ROSResult {
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
    ): ROSResult {
        val uniqueROSId = RandomStringUtils.randomAlphanumeric(5)
        return updateOrCreateROS(
            owner = owner,
            repository = repository,
            rosId = uniqueROSId,
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
    ): ROSResult {
        val validationStatus = JSONValidator.validateJSON(content.ros)
        if (!validationStatus.valid)
            return PostROSResult(
                ProcessingStatus.ROSNotValid,
                validationStatus.errors?.joinToString("\n") { it.error }.toString()
            )

        val encryptedData =
            SopsEncryptorForYaml.encrypt(content.ros, sopsEncryptorHelper)
                ?: return PostROSResult(
                    ProcessingStatus.EncrptionFailed,
                    "Klarte ikke kryptere ROS"
                )

        try {
            githubConnector.updateOrCreateDraft(
                owner = owner,
                repository = repository,
                rosId = rosId,
                fileContent = encryptedData,
                accessToken = accessToken
            )

            return PostROSResult(ProcessingStatus.UpdatedROS, "")
        } catch (e: Exception) {
            return PostROSResult(
                ProcessingStatus.ErrorWhenUpdatingROS,
                "Feilet med feilemelding ${e.message} for ros med id $rosId"
            )
        }
    }

    fun fetchAllROSDraftsSentToPublication(
        owner: String,
        repository: String,
        accessToken: String
    ): List<GithubPullRequestObject> = githubConnector.fetchAllPullRequestsForROS(owner, repository, accessToken)

    fun publishROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ): GithubPullRequestObject? =
        githubConnector.createPullRequestForPublishingROS(owner, repository, rosId, accessToken)

}
