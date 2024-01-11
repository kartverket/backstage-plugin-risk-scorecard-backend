package no.kvros.ros

import no.kvros.encryption.SopsEncryptorForYaml
import org.springframework.stereotype.Service
import java.util.*

@Service
class ROSService(
    private val githubConnector: GithubConnector,
) {
    fun fetchROSesFromGithub(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        githubConnector
            .fetchROSesFromGithub(owner, repository, pathToRoser, accessToken)
            ?.mapNotNull { SopsEncryptorForYaml.decrypt(ciphertext = it) }


    fun postNewROSToGithub(
        owner: String,
        repository: String,
        accessToken: String,
        content: String
    ): String? = githubConnector.writeToGithub(
        owner, repository, accessToken,
        GithubWritePayload(
            message = "Yeehaw dette er en ny ros",
            content = Base64.getEncoder().encodeToString(content.toByteArray()),
        ),
    )
}
