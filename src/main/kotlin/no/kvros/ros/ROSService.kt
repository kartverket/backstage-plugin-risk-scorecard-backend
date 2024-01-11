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
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ) :String? {
        val validationStatus  = JSONValidator.validateJSON(ros.ros)
        println("VALIDATIONSTATUS::::" + validationStatus)
        if (validationStatus.valid) {
          val encryptedData = SopsEncryptorForYaml.encrypt(publicKey , ros.ros)
                ?.let{
                  ROSWrapperObject(it)
                }
        println("ENCDATA:::: " + encryptedData)
        githubConnector
            .fetchROSesFromGithub(owner, repository, pathToRoser, accessToken)
            ?.let { it.mapNotNull { SopsEncryptorForYaml.decrypt(ciphertext = it) } }
        }
        return validationStatus.errors.toString()
    }


        // Legg til at det hvis !valid, så sender man tilbake erroroutputen tilbake til frontend :))


}
