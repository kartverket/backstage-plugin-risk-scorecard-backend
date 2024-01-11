package no.kvros.ros

import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ROSService(
    private val githubConnector: GithubConnector,
    @Value("\${sops.publicKey}")
    private val publicKey: String
) {
    fun fetchROSesFromGithub(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        githubConnector
            .fetchROSesFromGithub(owner, repository, pathToRoser, accessToken)
            ?.let { it.mapNotNull { SopsEncryptorForYaml.decrypt(ciphertext = it) } }


    fun postROSToGithub(
        ros: ROSWrapperObject,
    ) :String? {
        val validationStatus  = JSONValidator.validateJSON(ros.ros)
        if (validationStatus.valid) {
          val encryptedData = SopsEncryptorForYaml.encrypt(publicKey , ros.ros)
                ?.let{
                  return "success"
                }
        }
        return validationStatus.errors?.last()?.error
    }
}
