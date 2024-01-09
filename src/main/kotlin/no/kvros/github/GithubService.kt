package no.kvros.github

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
    ): List<String>? {
        val res = githubConnector.fetchROSes(owner, repository, pathToRoser, accessToken)
        // Decrypt data
        return res
    }
}
