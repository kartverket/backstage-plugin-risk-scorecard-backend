package no.kvros.ros

import no.kvros.utils.decrypt.decryptYamlData
import org.springframework.stereotype.Service

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
            ?.let { it.mapNotNull { decryptYamlData(it) } }
}
