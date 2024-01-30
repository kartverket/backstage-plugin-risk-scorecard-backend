package no.kvros.ros

import no.kvros.encryption.SopsEncryptionKeyProvider
import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.encryption.SopsEncryptorHelper
import no.kvros.encryption.SopsProviderAndCredentials
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.*


data class ROSResult(
    val status: ProcessingStatus,
    val statusMessage: String
)

enum class ProcessingStatus(val message: String) {
    ROSNotValid("ROS is not valid according to JSON-Schema"),
    EncrptionFailed("Kryptering av ROS feilet"),
    CouldNotCreateBranch("Could not create new branch for ROS"),
    NewBranchCreatedForNewROS("Created new branch for new ROS"),
    NewBranchCreatedForExistingROS("Created new branch for existing ROS"),
    ExistingROSBranch("Using existing branch for ROS"),
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
        path: String,
        accessToken: String,
    ): List<String>? =
        githubConnector
            .fetchPublishedROSes(owner, repository, path, accessToken)
            ?.let { it.mapNotNull { SopsEncryptorForYaml.decrypt(ciphertext = it, sopsEncryptorHelper) } }


    fun fetchROS(
        owner: String,
        repository: String,
        path: String,
        id: String,
        accessToken: String,
    ): String? =
        githubConnector.fetchPublishedROS(owner, repository, path, id, accessToken)
            ?.let { Base64.getMimeDecoder().decode(it).decodeToString() }
            ?.let { SopsEncryptorForYaml.decrypt(ciphertext = it, sopsEncryptorHelper) }

    fun fetchROSFilenames(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
    ): List<String> {
        val draftROSes = githubConnector.fetchAllROSBranches(owner, repository, accessToken)

        val publishedROSes = githubConnector.fetchPublishedROSFilenames(
            owner,
            repository,
            path,
            accessToken
        ) ?: emptyList()

        return publishedROSes + draftROSes.map {
            it.ref.split(
                "/"
            ).last() + draftPostfix
        }
    }

    private val draftPostfix = " (kladd)"

    fun updateOrCreateROS(
        owner: String,
        repository: String,
        rosDirectory: String,
        rosId: String,
        content: ROSWrapperObject,
        accessToken: String,
    ): ResponseEntity<ROSResult> {

        // Sjekk at fil er skrevet med riktige verdier for gitt JSon-schema
        val validationStatus = JSONValidator.validateJSON(content.ros)
        if (!validationStatus.valid)
            return ResponseEntity.badRequest().body(
                ROSResult(
                    ProcessingStatus.ROSNotValid,
                    validationStatus.errors?.joinToString("\n") { it.error }.toString()
                )
            )

        val pathToROS = "${rosDirectory}/$rosId.ros.yaml"

        val shaForExisingROS = githubConnector.fetchLatestSHAOfFileInMain(
            owner = owner,
            repository = repository,
            path = pathToROS,
            accessToken = accessToken,
        )

        val encryptedData =
            SopsEncryptorForYaml.encrypt(content.ros, sopsEncryptorHelper)
                ?: return ResponseEntity.internalServerError().body(
                    ROSResult(
                        ProcessingStatus.EncrptionFailed,
                        "Klarte ikke kryptere ROS" /*TODO: Legge ved hvorfor det feilet*/
                    )
                )


        val writeStatus = if (shaForExisingROS != null) writeROS(
            owner, repository, pathToROS, GithubWriteToFilePayload(
                message = "Ny ROS",
                content = Base64.getEncoder().encodeToString(encryptedData.toByteArray()),
                sha = shaForExisingROS,
            ), accessToken
        )
        else writeROS(
            owner, repository, pathToROS, GithubWriteToFilePayload(
                message = "Ny ROS",
                content = Base64.getEncoder().encodeToString(encryptedData.toByteArray()),
                sha = null,
            ), accessToken
        )

        return ResponseEntity.ok().body(ROSResult(ProcessingStatus.ExistingROSBranch, ""))
    }

    private fun writeROS(
        owner: String,
        repository: String,
        fileToROS: String,
        updateWithROSPayload: GithubWriteToFilePayload,
        accessToken: String
    ): ResponseEntity<String?> {
        return ResponseEntity.ok(
            githubConnector.writeToFile(
                owner = owner,
                repository = repository,
                path = fileToROS,
                accessToken = accessToken,
                writePayload = updateWithROSPayload,
            ),
        )
    }
}
