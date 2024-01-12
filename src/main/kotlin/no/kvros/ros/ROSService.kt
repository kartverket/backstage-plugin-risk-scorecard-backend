package no.kvros.ros

import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.*

@Service
class ROSService(
    private val githubConnector: GithubConnector,
    @Value("\${sops.publicKey}")
    private val publicKey: String,
) {
    fun fetchROSesFromGithub(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
    ): List<String>? =
        githubConnector
            .fetchROSesFromGithub(owner, repository, path, accessToken)
            ?.let { it.mapNotNull { SopsEncryptorForYaml.decrypt(ciphertext = it) } }

    fun postNewROSToGithub(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
        content: ROSWrapperObject,
    ): ResponseEntity<String?> {
        val validationStatus = JSONValidator.validateJSON(content.ros)
        if (!validationStatus.valid) {
            return ResponseEntity.badRequest().body(validationStatus.errors?.last()?.error)
        }

        val encryptedData =
            SopsEncryptorForYaml.encrypt(publicKey, content.ros)
                ?: return ResponseEntity.internalServerError().body("Kryptering feilet")

        return ResponseEntity.ok(
            githubConnector.writeToGithub(
                owner = owner,
                repository = repository,
                path = path,
                accessToken = accessToken,
                writePayload =
                    GithubWritePayload(
                        message = "Yeehaw dette er en ny ros",
                        content = Base64.getEncoder().encodeToString(encryptedData.toByteArray()),
                    ),
            ),
        )
    }
}
