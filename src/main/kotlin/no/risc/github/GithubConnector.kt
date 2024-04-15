package no.risc.github

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Base64
import java.util.Date
import no.risc.github.GithubHelper.toReferenceObjects
import no.risc.infra.connector.WebClientConnector
import no.risc.infra.connector.models.Email
import no.risc.infra.connector.models.UserContext
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
import no.risc.risc.models.FileContentDTO
import no.risc.risc.models.FileNameDTO
import no.risc.risc.models.ShaResponseDTO

data class GithubContentResponse(
    val data: String?,
    val status: GithubStatus,
) {
    fun data(): String = data!!
}

data class GithubRosIdentifiersResponse(
    val ids: List<RiScIdentifier>,
    val status: GithubStatus,
)

enum class GithubStatus {
    NotFound,
    Unauthorized,
    ContentIsEmpty,
    Success,
    RequestResponseBodyError,
    InternalError, // TODO
}

data class GithubWriteToFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branchName: String,
    val author: Author,
) {
    fun toContentBody(): String =
        when (sha) {
            null -> "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\", \"committer\": { \"name\":\"${author.name}\", \"email\":\"${author.email.value}\", \"date\":\"${author.formattedDate()}\" }"
            else -> "{\"message\":\"$message\", \"content\":\"$content\", \"sha\":\"$sha\", \"branch\": \"$branchName\", \"committer\": { \"name\":\"${author.name}\", \"email\":\"${author.email.value}\", \"date\":\"${author.formattedDate()}\" }"
        }
}

data class Author(val name: String, val email: Email, val date: Date) {
    fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)
}

@Component
class GithubConnector(
    @Value("\${github.repository.ros-folder-path}") private val riScFolderPath: String,
    @Value("\${filename.postfix}") private val filenamePostfix: String,
) :
    WebClientConnector("https://api.github.com/repos") {

    fun fetchSopsConfig(
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
    ): String? {
        return try {
            getGithubResponse(GithubHelper.uriToFindSopsConfig(owner, repository), githubAccessToken.value)
                .fileContent()
                ?.value
                ?.decodeBase64()
        } catch (e: Exception) {
            null
        }
    }

    private fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(this).decodeToString()

    fun fetchAllRiScIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String,
    ): GithubRosIdentifiersResponse {
        val draftROSes =
            try {
                fetchROSIdentifiersDrafted(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        val publishedROSes =
            try {
                fetchPublishedROSIdentifiers(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        val rosSentForApproval =
            try {
                fetchROSIdentifiersSentForApproval(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        return GithubRosIdentifiersResponse(
            status = GithubStatus.Success,
            ids =
                combinePublishedDraftAndSentForApproval(
                    draftRosList = draftROSes,
                    sentForApprovalList = rosSentForApproval,
                    publishedRosList = publishedROSes,
                ),
        )
    }

    fun combinePublishedDraftAndSentForApproval(
        draftRosList: List<RiScIdentifier>,
        sentForApprovalList: List<RiScIdentifier>,
        publishedRosList: List<RiScIdentifier>,
    ): List<RiScIdentifier> {
        val draftIds = draftRosList.map { it.id }
        val sentForApprovalsIds = sentForApprovalList.map { it.id }
        val publishedROSIdentifiersNotInDraftList =
            publishedRosList.filter { it.id !in draftIds && it.id !in sentForApprovalsIds }
        val draftROSIdentifiersNotInSentForApprovalsList = draftRosList.filter { it.id !in sentForApprovalsIds }

        return sentForApprovalList + publishedROSIdentifiersNotInDraftList + draftROSIdentifiersNotInSentForApprovalsList
    }

    fun fetchPublishedRiSc(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponse(
                    "/$owner/$repository/contents/$riScFolderPath/$id.$filenamePostfix.yaml",
                    accessToken,
                ).fileContent()?.value

            if (fileContent == null) {
                GithubContentResponse(null, GithubStatus.ContentIsEmpty)
            } else {
                GithubContentResponse(
                    fileContent,
                    GithubStatus.Success,
                )
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    fun fetchDraftedRiScContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponse(
                    uri = GithubHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, id, filenamePostfix=filenamePostfix),
                    accessToken = accessToken,
                ).fileContent()?.value

            if (fileContent == null) {
                GithubContentResponse(null, GithubStatus.ContentIsEmpty)
            } else {
                GithubContentResponse(
                    fileContent,
                    GithubStatus.Success,
                )
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    private fun mapWebClientExceptionToGithubStatus(e: Exception): GithubStatus =
        when (e) {
            is WebClientResponseException ->
                when (e) {
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
    ): List<RiScIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFindRiScFiles(owner, repository, riScFolderPath),
            accessToken,
        ).rosIdentifiersPublished()

    private fun fetchROSIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFetchAllPullRequests(owner, repository),
            accessToken,
        ).pullRequestResponseDTOs().rosIdentifiersSentForApproval()

    private fun fetchROSIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        getGithubResponse(
            uri = GithubHelper.uriToFindAllRiScBranches(owner, repository),
            accessToken = accessToken,
        ).toReferenceObjects().rosIdentifiersDrafted()

    internal fun updateOrCreateDraft(
        owner: String,
        repository: String,
        rosId: String,
        fileContent: String,
        requiresNewApproval: Boolean,
        userContext: UserContext,
    ): Boolean {
        val accessToken = userContext.githubAccessToken
        val githubAuthor =
            Author(userContext.microsoftUser.name, userContext.microsoftUser.email, Date.from(Instant.now()))
        if (!branchForROSDraftExists(owner, repository, rosId, accessToken.value)) {
            createNewBranch(
                owner = owner,
                repository = repository,
                rosId = rosId,
                accessToken = accessToken.value,
            )
        }
        var hasClosedPR = false
        if (pullRequestForROSExists(owner, repository, rosId, accessToken.value) and requiresNewApproval) {
            closeExistingPullRequestForROS(owner, repository, rosId, accessToken.value)
            hasClosedPR = true
        }

        val latestShaForROS = getSHAForExistingROSDraftOrNull(owner, repository, rosId, accessToken.value)

        val commitMessage =
            if (latestShaForROS != null) "refactor: Oppdater ROS med id: $rosId" else "feat: Lag ny ROS med id: $rosId"

        putFileRequestToGithub(
            uri = GithubHelper.uriToPostContentOfFileOnDraftBranch(owner, repository, rosId, filenamePostfix = filenamePostfix),
            accessToken.value,
            GithubWriteToFilePayload(
                message = commitMessage,
                content = Base64.getEncoder().encodeToString(fileContent.toByteArray()),
                sha = latestShaForROS,
                branchName = rosId,
                author = githubAuthor,
            ),
        ).bodyToMono<String>().block()

        if (!requiresNewApproval and !pullRequestForROSExists(owner, repository, rosId, accessToken.value)) {
            createPullRequestForPublishingROS(owner, repository, rosId, requiresNewApproval, userContext)
        }
        return hasClosedPR
    }

    private fun closeExistingPullRequestForROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): String? {
        val pullRequestsForROS = fetchAllPullRequestsForROS(owner, repository, accessToken)
        val matchingPullRequest = pullRequestsForROS.find { it.head.ref == rosId }
        if (matchingPullRequest != null) {
            try {
                return closePullRequest(
                    uri = GithubHelper.uriToEditPullRequest(owner, repository, matchingPullRequest.number),
                    accessToken = accessToken,
                    closePullRequestBody = GithubHelper.bodyToClosePullRequest(),
                ).bodyToMono<String>().block()
            } catch (e: Exception) {
                println(e)
            }
        }
        return null
    }

    private fun getSHAForExistingROSDraftOrNull(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, rosId, filenamePostfix=filenamePostfix),
            accessToken,
        ).shaReponseDTO()?.value
    } catch (e: Exception) {
        null
    }

    private fun branchForROSDraftExists(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): Boolean = fetchBranchForROS(owner, repository, rosId, accessToken).isNotEmpty()

    private fun pullRequestForROSExists(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): Boolean {
        val rosesSentForApproval = fetchROSIdentifiersSentForApproval(owner, repository, accessToken)
        return rosesSentForApproval.any { it.id == rosId }
    }

    private fun fetchBranchForROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindExistingBranchForRiSc(owner, repository, rosId),
            accessToken,
        ).toReferenceObjects()
    } catch (e: Exception) {
        emptyList()
    }

    private fun fetchLatestShaForDefaultBranch(
        owner: String,
        repository: String,
        accessToken: String,
    ): String? =
        getGithubResponse(
            GithubHelper.uriToGetCommitStatus(owner, repository, "main"),
            accessToken,
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
            GithubHelper.uriToCreateNewBranchForRiSc(owner, repository),
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
        val githubResponse =
            try {
                getGithubResponse(
                    GithubHelper.uriToFetchAllPullRequests(owner, repository),
                    accessToken,
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
        requiresNewApproval: Boolean,
        userContext: UserContext,
    ): GithubPullRequestObject? {
        return postNewPullRequestToGithub(
            GithubHelper.uriToCreatePullRequest(owner, repository),
            userContext.githubAccessToken.value,
            GithubHelper.bodyToCreateNewPullRequest(owner, rosId, requiresNewApproval, userContext.microsoftUser),
        ).pullRequestResponseDTO()
    }

    private fun postNewPullRequestToGithub(
        uri: String,
        accessToken: String,
        pullRequestPayload: GithubCreateNewPullRequestPayload,
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(pullRequestPayload.toContentBody()), String::class.java)
        .retrieve()

    private fun closePullRequest(
        uri: String,
        accessToken: String,
        closePullRequestBody: String,
    ) = webClient
        .patch()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(closePullRequestBody), String::class.java)
        .retrieve()

    private fun postNewBranchToGithub(
        uri: String,
        accessToken: String,
        branchPayload: GithubCreateNewBranchPayload,
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
        writePayload: GithubWriteToFilePayload,
    ) = webClient
        .put()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(writePayload.toContentBody()), String::class.java)
        .retrieve()

    private fun ResponseSpec.fileContent(): FileContentDTO? = this.bodyToMono<FileContentDTO>().block()

    private fun ResponseSpec.shaReponseDTO(): ShaResponseDTO? = this.bodyToMono<ShaResponseDTO>().block()

    fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.rosIdentifiersPublished(): List<RiScIdentifier> =
        this.bodyToMono<List<FileNameDTO>>().block()
            ?.filter { it.value.endsWith(".ros.yaml") }
            ?.map { RiScIdentifier(it.value.substringBefore('.'), RiScStatus.Published) } ?: emptyList()

    fun List<GithubPullRequestObject>.rosIdentifiersSentForApproval(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.head.ref.split("/").last(), RiScStatus.SentForApproval) }
            .filter { it.id.startsWith("ros-") }

    fun List<GithubReferenceObject>.rosIdentifiersDrafted(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.ref.split("/").last(), RiScStatus.Draft) }

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
