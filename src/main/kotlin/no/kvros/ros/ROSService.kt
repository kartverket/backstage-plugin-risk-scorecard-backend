package no.kvros.ros

import no.kvros.encryption.SopsEncryptionKeyProvider
import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.encryption.SopsEncryptorHelper
import no.kvros.encryption.SopsProviderAndCredentials
import no.kvros.ros.ROSName.Companion.toROSIdWithDraftIdentificator
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
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

data class ROSName(
    val id: String,
    val draftIndicator: String? = null
) {
    companion object {
        fun String.toROSIdWithDraftIdentificator(): ROSName {
            val rosAndIndicator = this.replace(" ", "").split("(")
            return ROSName(
                id = rosAndIndicator.first().split("-").last(),
                draftIndicator = if (rosAndIndicator.size > 1) rosAndIndicator.last() else null
            )
        }
    }

    fun isDraft(): Boolean = draftIndicator != null && draftIndicator.contains("kladd")
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
        id: String,
        accessToken: String,
    ): String? {
        val rosName = id.toROSIdWithDraftIdentificator()
        if (!rosName.isDraft()) return fetchPublishedROS(owner, repository, id, accessToken)

        // Sjekke om branchen til denne fila finnes?

        return fetchDraftedROS(owner, repository, rosName, accessToken)
    }

    private fun fetchDraftedROS(
        owner: String,
        repository: String,
        rosName: ROSName,
        accessToken: String
    ): String? =
        githubConnector.fetchDraftedROSContent(owner, repository, rosName.id, accessToken)
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
    ): List<String> {
        // Sjekke om default-path finnes og gi feilmelding om dette?
        // Vil du opprette denne mappen?

        val draftROSes = try {
            githubConnector.fetchAllROSBranches(owner, repository, accessToken)
        } catch (e: Exception) {
            emptyList()
        }

        val publishedROSes = try {
            githubConnector.fetchPublishedROSFilenames(
                owner,
                repository,
                accessToken
            )
        } catch (e: Exception) {
            emptyList()
        }

        return publishedROSes + draftROSes.map {
            it.ref.split(
                "/"
            ).last() + draftPostfix
        }
    }

    fun updateOrCreateROS(
        owner: String,
        repository: String,
        rosReference: String, // todo: endre variabelnavn og på klassen
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

        val rosId = rosReference.toROSIdWithDraftIdentificator()

        try {
            githubConnector.updateOrCreateDraft(
                owner = owner,
                repository = repository,
                rosId = rosId.id,
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

}
