package no.kvros.ros

import no.kvros.github.*
import no.kvros.github.GithubHelper.toReferenceObjects
import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ContentResponseDTO
import no.kvros.ros.models.ROSContentDTO
import no.kvros.ros.models.ROSFilenameDTO
import no.kvros.ros.models.ShaResponseDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.*

data class GithubContentResponse(
    val data: String?,
    val status: GithubStatus
) {
    fun data(): String = data!!
}

data class GithubRosIdentifiersResponse(
    val ids: List<ROSIdentifier>,
    val status: GithubStatus
)

enum class GithubStatus {
    NotFound,
    Unauthorized,
    ContentIsEmpty,
    Success,
    RequestResponseBodyError,
    InternalError // TODO
}

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

    fun fetchAllRosIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String
    ): GithubRosIdentifiersResponse {
        val draftROSes = try {
            fetchROSIdentifiersDrafted(owner, repository, accessToken)
        } catch (e: Exception) {
            return GithubRosIdentifiersResponse(emptyList(), mapWebClientExceptionToGithubStatus(e))
        }

        val publishedROSes = try {
            fetchPublishedROSIdentifiers(
                owner,
                repository,
                accessToken
            )
        } catch (e: Exception) {
            return GithubRosIdentifiersResponse(emptyList(), mapWebClientExceptionToGithubStatus(e))
        }

        val rosSentForApproval = try {
            fetchROSIdentifiersSentForApproval(owner, repository, accessToken)
        } catch (e: Exception) {
            return GithubRosIdentifiersResponse(emptyList(), mapWebClientExceptionToGithubStatus(e))
        }


        return GithubRosIdentifiersResponse(
            status = GithubStatus.Success,
            ids = combinePublishedDraftAndSentForApproval(
                draftROSes,
                publishedROSes,
                rosSentForApproval
            ),
        )
    }

    fun combinePublishedDraftAndSentForApproval(
        draftRosList: List<ROSIdentifier>,
        sentForApprovalList: List<ROSIdentifier>,
        publishedRosList: List<ROSIdentifier>
    ): List<ROSIdentifier> {
        val draftIds = draftRosList.map { it.id }
        val sentForApprovalsIds = sentForApprovalList.map { it.id }
        val publisedROSIdentifiersNotInDraftList =
            publishedRosList.filter { it.id !in draftIds && it.id !in sentForApprovalsIds }
        val draftROSIdentifiersNotInSentForApprovalsList = draftRosList.filter { it.id !in sentForApprovalsIds }

        return sentForApprovalList + publisedROSIdentifiersNotInDraftList + draftROSIdentifiersNotInSentForApprovalsList
    }

    fun fetchPublishedROS(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse = try {
        val fileContent = getGithubResponse(
            "/$owner/$repository/contents/$defaultROSPath/$id.ros.yaml",
            accessToken
        ).rosContent()?.content

        if (fileContent == null) GithubContentResponse(null, GithubStatus.ContentIsEmpty)
        else GithubContentResponse(
            fileContent, GithubStatus.Success
        )
    } catch (e: Exception) {
        GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
    }


    fun fetchDraftedROSContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse = try {
        val fileContent = getGithubResponse(
            uri = GithubHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, id),
            accessToken = accessToken
        ).rosContent()?.content

        if (fileContent == null) GithubContentResponse(null, GithubStatus.ContentIsEmpty)
        else GithubContentResponse(
            fileContent, GithubStatus.Success
        )
    } catch (e: Exception) {
        GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
    }

    private fun mapWebClientExceptionToGithubStatus(e: Exception): GithubStatus = when (e) {
        is WebClientResponseException -> when (e) {
            is WebClientResponseException.NotFound -> GithubStatus.NotFound
            is WebClientResponseException.Unauthorized -> GithubStatus.Unauthorized
            is WebClientResponseException.UnprocessableEntity -> GithubStatus.RequestResponseBodyError
            else -> GithubStatus.InternalError
        }

        else -> GithubStatus.InternalError
    }

    private fun fetchPublishedROSIdentifiers(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFindRosFiles(owner, repository, defaultROSPath),
            accessToken
        ).rosIdentifiersPublished()


    private fun fetchROSIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> = getGithubResponse(
        GithubHelper.uriToFetchAllPullRequests(owner, repository),
        accessToken
    ).pullRequestResponseDTOs().rosIdentifiersSentForApproval()

    private fun fetchROSIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> = getGithubResponse(
        uri = GithubHelper.uriToFindAllRosBranches(owner, repository),
        accessToken = accessToken
    ).toReferenceObjects().rosIdentifiersDrafted()


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

        val latestShaForROS = getSHAForExistingROSDraftOrNull(owner, repository, rosId, accessToken)

        val commitMessage = if (latestShaForROS != null) "refactor: Oppdater ROS" else "feat: Lag ny ROS"

        return putFileRequestToGithub(
            uri = GithubHelper.uriToPostContentOfFileOnDraftBranch(owner, repository, rosId),
            accessToken,
            GithubWriteToFilePayload(
                message = commitMessage,
                content = Base64.getEncoder().encodeToString(fileContent.toByteArray()),
                sha = latestShaForROS,
                branchName = rosId
            )
        ).bodyToMono<String>().block()
    }

    private fun getSHAForExistingROSDraftOrNull(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, rosId),
            accessToken
        ).contentReponseDTO()?.sha
    } catch (e: Exception) {
        null
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
            GithubHelper.uriToFindExistingBranchForROS(owner, repository, rosId),
            accessToken
        ).toReferenceObjects()
    } catch (e: Exception) {
        emptyList()
    }

    private fun fetchLatestShaForDefaultBranch(owner: String, repository: String, accessToken: String): String? =
        getGithubResponse(
            GithubHelper.uriToGetCommitStatus(owner, repository, "main"),
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


        return postNewBranchToGithub(
            GithubHelper.uriToCreateNewBranchForROS(owner, repository),
            accessToken,
            GithubHelper.bodyToCreateNewBranchForROSFromMain(rosId, latestShaForMainBranch),
        )
            .bodyToMono<String>()
            .block()
    }


    fun fetchAllPullRequestsForROS(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubPullRequestObject> {
        val githubResponse = try {
            getGithubResponse(
                GithubHelper.uriToFetchAllPullRequests(owner, repository),
                accessToken
            ).pullRequestResponseDTOs()
        } catch (e: Exception) {
            emptyList()
        }

        return githubResponse
    }


    fun createPullRequestForPublishingROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): GithubPullRequestObject? {
        return postNewPullRequestToGithub(
            GithubHelper.uriToCreatePullRequest(owner, repository),
            accessToken,
            GithubHelper.bodyToCreateNewPullRequest(owner, rosId)
        ).pullRequestResponseDTO()
    }

    private fun postNewPullRequestToGithub(
        uri: String,
        accessToken: String,
        pullRequestPayload: GithubCreateNewPullRequestPayload
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(pullRequestPayload.toContentBody()), String::class.java)
        .retrieve()


    private fun postNewBranchToGithub(
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

    private fun ResponseSpec.rosContent(): ROSContentDTO? =
        this.bodyToMono<ROSContentDTO>().block()

    private fun ResponseSpec.contentReponseDTO(): ContentResponseDTO? =
        this.bodyToMono<ContentResponseDTO>().block()

    fun ResponseSpec.shaReponseDTO(): ShaResponseDTO? =
        this.bodyToMono<ShaResponseDTO>().block()

    fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()


    fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? =
        this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.rosIdentifiersPublished(): List<ROSIdentifier> =
        this.bodyToMono<List<ROSFilenameDTO>>().block()
            ?.map { ROSIdentifier(id = it.name.substringBefore('.'), status = ROSStatus.Published) } ?: emptyList()

    fun List<GithubPullRequestObject>.rosIdentifiersSentForApproval(): List<ROSIdentifier> =
        this.map { ROSIdentifier(it.head.ref.split("/").last(), ROSStatus.SentForApproval) }

    fun List<GithubReferenceObject>.rosIdentifiersDrafted(): List<ROSIdentifier> =
        this.map { ROSIdentifier(id = it.ref.split("/").last(), status = ROSStatus.Draft) }

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