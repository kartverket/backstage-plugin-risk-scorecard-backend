package no.kvros.ros

import no.kvros.encryption.SopsEncryptionKeyProvider
import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.encryption.SopsEncryptorHelper
import no.kvros.encryption.SopsProviderAndCredentials
import no.kvros.ros.models.ROSAndFilename
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.Base64

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

    fun fetchROSFromGithub(
        owner: String,
        repository: String,
        path: String,
        id: String,
        accessToken: String,
    ): String? =
        githubConnector.fetchROSFromGithub(owner, repository, path, id, accessToken)
            ?.let { Base64.getMimeDecoder().decode(it).decodeToString() }
            ?.let { SopsEncryptorForYaml.decrypt(ciphertext = it, sopsEncryptorHelper) }

    fun fetchAllROSesFromGithub(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
    ): List<ROSAndFilename>? =
        githubConnector.fetchROSesFromGithub(owner, repository, path, accessToken)?.let { rosAndFilename ->
            rosAndFilename.mapNotNull {
                val decryptedROS = SopsEncryptorForYaml.decrypt(ciphertext = it.ros, sopsEncryptorHelper)
                if (decryptedROS != null) {
                    ROSAndFilename(decryptedROS, it.filename)
                } else {
                    null
                }
            }
        }


    fun fetchROSFilenamesFromGithub(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
    ): List<String>? = githubConnector.fetchROSFilenamesFromGithub(owner, repository, path, accessToken)

    fun postNewROSToGithub(
        owner: String,
        repository: String,
        rosFilePath: String,
        accessToken: String,
        content: ROSWrapperObject,
    ): ResponseEntity<String?> {
        val validationStatus = JSONValidator.validateJSON(content.ros)
        if (!validationStatus.valid) {
            return ResponseEntity.badRequest().body(validationStatus.errors?.joinToString("\n") { it.error })
        }

        val encryptedData =
            SopsEncryptorForYaml.encrypt(content.ros, sopsEncryptorHelper)
                ?: return ResponseEntity.internalServerError().body("Kryptering feilet")

        val shaForExisingROS = githubConnector.getRosSha(owner, repository, accessToken, rosFilePath)

        return ResponseEntity.ok(
            githubConnector.writeToGithub(
                owner = owner,
                repository = repository,
                path = rosFilePath,
                accessToken = accessToken,
                writePayload =
                    GithubWritePayload(
                        message = if (shaForExisingROS == null) "Yeehaw new ROS" else "Yeehaw oppdatert ROS",
                        content = Base64.getEncoder().encodeToString(encryptedData.toByteArray()),
                        sha = shaForExisingROS,
                    ),
            ),
        )
    }
}
