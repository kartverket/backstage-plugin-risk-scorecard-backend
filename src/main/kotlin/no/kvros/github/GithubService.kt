package no.kvros.github

import no.kvros.utils.decrypt.decryptYamlData
import org.springframework.stereotype.Service

@Service
class GithubService(
    private val githubConnector: GithubConnector,
) {
    fun fetchROSes(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        githubConnector
            .fetchROSes(owner, repository, pathToRoser, accessToken)
            ?.let { it.mapNotNull { decryptYamlData(it) } }
}
