package no.kvros.ros

import no.kvros.github.GithubCreateNewBranchPayload
import no.kvros.github.GithubReferenceHelper
import no.kvros.github.GithubReferenceHelper.toReferenceObjects
import no.kvros.github.GithubReferenceObject
import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.*

data class GithubWriteToFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branchName: String
) {
    fun toContentBody(): String =
        when (sha) {
            null -> "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\"}"
            else -> "{\"message\":\"$message\", \"content\":\"$content\", \"sha\":\"$sha\", \"branch\": \"$branchName\"}"
        }
}

@Component
class GithubConnector(@Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String) :
    WebClientConnector("https://api.github.com/repos") {
    fun fetchPublishedROS(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): String? =
        getGithubResponse(
            "/$owner/$repository/contents/$defaultROSPath/$id.ros.yaml",
            accessToken
        ).rosContent()?.content


    fun fetchDraftedROSContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): String? = try {
        getGithubResponse(
            uri = GithubReferenceHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, id),
            accessToken = accessToken
        ).rosContent()?.content
    } catch (_: Exception) {
        null
    }

    fun fetchPublishedROSFilenames(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<String> =
        getGithubResponse("/$owner/$repository/contents/$defaultROSPath", accessToken).rosFilenames() // TODO : helper
            ?.map { it.name.substringBefore('.') } ?: emptyList()

    internal fun updateOrCreateDraft(
        owner: String,
        repository: String,
        rosId: String,
        fileContent: String,
        accessToken: String
    ): String? {
        if (!branchForROSDraftExists(owner, repository, rosId, accessToken)) createNewBranch(
            owner = owner,
            repository = repository,
            rosId = rosId,
            accessToken = accessToken,
        )

        val latestShaForROS = getGithubResponse(
            GithubReferenceHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, rosId),
            accessToken
        ).contentReponseDTO()?.sha

        val commitMessage = if (latestShaForROS != null) "refactor: Oppdater ROS" else "feat: Lag ny ROS"

        return putFileRequestToGithub(
            uri = GithubReferenceHelper.uriToPostContentOfFileOnDraftBranch(owner, repository, rosId),
            accessToken,
            GithubWriteToFilePayload(
                message = commitMessage,
                content = Base64.getEncoder().encodeToString(fileContent.toByteArray()),
                sha = latestShaForROS,
                branchName = "ros-$rosId"
            )
        ).bodyToMono<String>().block()
    }

    private fun branchForROSDraftExists(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ): Boolean = fetchBranchForROS(owner, repository, rosId, accessToken).isNotEmpty()

    private fun fetchBranchForROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ) = try {
        getGithubResponse(
            GithubReferenceHelper.uriToFindExistingBranchForROS(owner, repository, rosId),
            accessToken
        ).toReferenceObjects()
    } catch (e: Exception) {
        emptyList()
    }

    fun fetchAllROSBranches(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubReferenceObject> = getGithubResponse(
        uri = GithubReferenceHelper.uriToFindAllRosBranches(owner, repository),
        accessToken = accessToken
    ).toReferenceObjects()

    private fun fetchLatestShaForDefaultBranch(owner: String, repository: String, accessToken: String): String? =
        getGithubResponse(
            GithubReferenceHelper.uriToGetCommitStatus(owner, repository, "main"),
            accessToken
        ).shaReponseDTO()?.value


    fun createNewBranch(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): String? {
        val latestShaForMainBranch =
            fetchLatestShaForDefaultBranch(owner, repository, accessToken) ?: return null


        return postBranchRequestToGithub(
            GithubReferenceHelper.uriToCreateNewBranchForROS(owner, repository),
            accessToken,
            GithubReferenceHelper.bodyToCreateNewBranchForROSFromMain(rosId, latestShaForMainBranch),
        )
            .bodyToMono<String>()
            .block()

    }

    private fun postBranchRequestToGithub(
        uri: String,
        accessToken: String,
        branchPayload: GithubCreateNewBranchPayload
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(branchPayload.toContentBody()), String::class.java)
        .retrieve()


    private fun putFileRequestToGithub(
        uri: String,
        accessToken: String,
        writePayload: GithubWriteToFilePayload
    ) = webClient
        .put()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(writePayload.toContentBody()), String::class.java)
        .retrieve()

    private fun ResponseSpec.rosFilenames(): List<ROSFilenameDTO>? =
        this.bodyToMono<List<ROSFilenameDTO>>().block()

    private fun ResponseSpec.rosContent(): ROSContentDTO? =
        this.bodyToMono<ROSContentDTO>().block()

    private fun ResponseSpec.contentReponseDTO(): ContentResponseDTO? =
        this.bodyToMono<ContentResponseDTO>().block()


    private fun ResponseSpec.downloadUrls(): List<ROSDownloadUrlDTO>? =
        this.bodyToMono<List<ROSDownloadUrlDTO>>().block()

    fun WebClient.ResponseSpec.shaReponseDTO(): ShaResponseDTO? =
        this.bodyToMono<ShaResponseDTO>().block()


    private fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()
}