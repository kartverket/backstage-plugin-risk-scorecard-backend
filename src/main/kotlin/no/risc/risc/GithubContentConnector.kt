package no.risc.risc

import no.risc.github.GithubAccessToken
import no.risc.github.GithubHelper
import no.risc.github.GithubPullRequestObject
import no.risc.github.GithubReferenceObject
import no.risc.github.GithubReferenceObjectDTO
import no.risc.github.models.FileContentDTO
import no.risc.github.models.ShaResponseDTO
import no.risc.infra.connector.WebClientConnector
import no.risc.risc.models.UserInfo
import no.risc.utils.encodeBase64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.text.SimpleDateFormat
import java.util.Date

data class BranchInfo(
    val exists: Boolean,
    val latestSha: String = "",
) {
    companion object {
        val BRANCH_DOES_NOT_EXIST = BranchInfo(exists = false)
    }
}

data class PrInfo(
    val exists: Boolean,
) {
    companion object {
        val PR_DOES_NOT_EXIST = PrInfo(exists = false)
    }
}

data class UpdateContentInfo(
    val success: Boolean,
) {
    companion object {
        val NOT_SUCCESSFUL = UpdateContentInfo(success = false)
        val SUCCESSFUL = UpdateContentInfo(success = true)
    }
}

data class CreateContentInfo(
    val success: Boolean,
) {
    companion object {
        val NOT_SUCCESSFUL = CreateContentInfo(success = false)
        val SUCCESSFUL = CreateContentInfo(success = true)
    }
}

class GithubContentConnector(
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${filename.prefix}") private val filenamePrefix: String,
    private val githubHelper: GithubHelper,
) : WebClientConnector("https://api.github.com/repos") {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun createBranch(
        owner: String,
        repository: String,
        latestShaOfMain: String,
        riscId: String,
        accessToken: GithubAccessToken,
    ): BranchInfo {
        val result =
            try {
                githubPOST(
                    uri = githubHelper.uriToCreateNewBranchForRiSc(owner, repository),
                    accessToken = accessToken.value,
                    githubPostPayload = GithubBranchPayload("refs/heads/$riscId", latestShaOfMain),
                ).shaResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not create branch for RiSc with id: $riscId", e)

                null
            }

        return when (result.isNullOrBlank()) {
            true -> BranchInfo.BRANCH_DOES_NOT_EXIST
            false -> BranchInfo(exists = true, latestSha = result)
        }
    }

    fun createContent(
        owner: String,
        repository: String,
        fileContent: String,
        riscId: String,
        commitMessage: String = "Created new RiSc with id: $riscId",
        author: Author,
        accessToken: GithubAccessToken,
    ): CreateContentInfo {
        val result =
            try {
                githubPUT(
                    githubHelper.uriToPutRiScOnDraftBranch(owner, repository, riscId),
                    accessToken.value,
                    GithubUpdateFilePayload(
                        message = commitMessage,
                        content = fileContent.encodeBase64(),
                        sha = null,
                        branchName = riscId,
                        author = author,
                    ),
                ).fileContentResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not update RiSc with id: $riscId", e)

                null
            }

        return when (result.isNullOrBlank()) {
            true -> CreateContentInfo.NOT_SUCCESSFUL
            false -> CreateContentInfo.SUCCESSFUL
        }
    }

    fun updateContent(
        owner: String,
        repository: String,
        sha: String,
        updatedFileContent: String,
        riscId: String,
        commitMessage: String = "Update RiSc with id: $riscId",
        author: Author,
        accessToken: GithubAccessToken,
    ) {
        val result =
            try {
                githubPUT(
                    githubHelper.uriToPutRiScOnDraftBranch(owner, repository, riscId),
                    accessToken.value,
                    GithubUpdateFilePayload(
                        message = commitMessage,
                        content = updatedFileContent.encodeBase64(),
                        sha = sha,
                        branchName = riscId,
                        author = author,
                    ),
                ).fileContentResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not update RiSc with id: $riscId", e)

                null
            }

        when (result.isNullOrBlank()) {
            true -> UpdateContentInfo.NOT_SUCCESSFUL
            false -> UpdateContentInfo.SUCCESSFUL
        }
    }

    fun doesBranchExist(
        owner: String,
        repository: String,
        riscId: String,
        accessToken: GithubAccessToken,
    ): BranchInfo {
        val result =
            try {
                githubGET(
                    uri = githubHelper.uriToFindRiScOnDraftBranch(owner, repository, riscId),
                    accessToken = accessToken.value,
                ).shaResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not find branch for risc id $riscId", e)

                null
            }

        return when (result.isNullOrBlank()) {
            true -> BranchInfo.BRANCH_DOES_NOT_EXIST
            false -> BranchInfo(exists = true, latestSha = result)
        }
    }

    fun createPR(
        owner: String,
        repository: String,
        riscId: String,
        accessToken: GithubAccessToken,
        userInfo: UserInfo,
    ): PrInfo {
        val result =
            try {
                githubPOST(
                    uri = githubHelper.uriToCreatePullRequest(owner, repository),
                    accessToken = accessToken.value,
                    githubPostPayload =
                        GithubPullRequestPayload(
                            title = "Updated risk scorecard",
                            body =
                                "${userInfo.name} (${userInfo.email}) has approved the risk " +
                                    "scorecard. Merge the pull request to include the changes in the main branch.",
                            repositoryOwner = owner,
                            riScId = riscId,
                            baseBranch = "main",
                        ),
                ).pullRequestResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not create PR for RiSc with id: $riscId")
                null
            }

        return when (result != null) {
            true -> PrInfo(exists = true)
            false -> PrInfo.PR_DOES_NOT_EXIST
        }
    }

    fun closePR(
        owner: String,
        repository: String,
        accessToken: GithubAccessToken,
        riscId: String,
    ): Boolean {
        val pullRequestsForRiSc =
            try {
                githubGET(
                    githubHelper.uriToFetchAllPullRequests(owner, repository),
                    accessToken = accessToken.value,
                ).pullRequestResponseDTOs()
            } catch (e: Exception) {
                logger.error("Could not fetch PRs from repository: $repository", e)

                null
            }

        when (pullRequestsForRiSc != null && pullRequestsForRiSc.isNotEmpty()) {
            true -> {
                val prForRiSc = pullRequestsForRiSc.find { it.head.ref == riscId }
                when (prForRiSc) {
                    null -> {}
                    else -> {
                        githubPATCH(
                            uri = githubHelper.uriToEditPullRequest(owner, repository, prForRiSc.number),
                            accessToken = accessToken.value,
                            githubPatchPayload = GithubClosePullRequestPayload(),
                        ).bodyToMono<String>().block()
                    }
                }
            }
            false -> {}
        }

        return true
    }

    suspend fun doesPRExist(
        owner: String,
        repository: String,
        riscId: String,
        accessToken: GithubAccessToken,
    ): PrInfo {
        val result =
            try {
                val githubResponse =
                    githubGET(
                        uri = githubHelper.uriToFindAllRiScBranches(owner, repository),
                        accessToken.value,
                    ).awaitBody<List<GithubReferenceObjectDTO>>().map {
                        it.toInternal()
                    }

                githubResponse.riScIdentifiersDrafted()
            } catch (e: Exception) {
                logger.error("Could not fetch PRs", e)

                null
            }

        return when (result != null && result.any { it.id == riscId }) {
            true -> PrInfo(exists = true)
            false -> PrInfo.PR_DOES_NOT_EXIST
        }
    }

    private fun List<GithubReferenceObject>.riScIdentifiersDrafted(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.ref.split("/").last(), RiScStatus.Draft) }

    private fun githubGET(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient
            .get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()

    private fun githubPUT(
        uri: String,
        accessToken: String,
        githubPutPayload: GithubPutPayload,
    ) = webClient
        .put()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(githubPutPayload.toContentBody()), String::class.java)
        .retrieve()

    private fun githubPOST(
        uri: String,
        accessToken: String,
        githubPostPayload: GithubPostPayload,
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(githubPostPayload.toContentBody()), String::class.java)
        .retrieve()

    private fun githubPATCH(
        uri: String,
        accessToken: String,
        githubPatchPayload: GithubPatchPayload,
    ) = webClient
        .patch()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(githubPatchPayload), String::class.java)
        .retrieve()

    private fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    private fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.shaResponseDTO(): String? = this.bodyToMono<ShaResponseDTO>().block()?.value

    private fun ResponseSpec.fileContentResponseDTO(): String? = this.bodyToMono<FileContentDTO>().block()?.value
}

data class Author(
    val name: String?,
    val email: String?,
    val date: Date,
) {
    fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)
}

data class GithubUpdateFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branchName: String,
    val author: Author,
) : GithubPutPayload {
    override fun toContentBody(): String =
        when (sha) {
            null ->
                "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\", \"committer\": " +
                    "{ \"name\":\"${author.name}\", \"email\":\"${author.email}\", \"date\":\"${author.formattedDate()}\" }"

            else ->
                "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\", \"committer\": " +
                    "{ \"name\":\"${author.name}\", \"email\":\"${author.email}\", \"date\":\"${author.formattedDate()}\" }, " +
                    "\"sha\":\"$sha\""
        }
}

data class GithubBranchPayload(
    val nameOfNewBranch: String,
    val shaOfLatestMain: String,
) : GithubPostPayload {
    override fun toContentBody(): String = "{ \"ref\":\"$nameOfNewBranch\", \"sha\": \"$shaOfLatestMain\" }"
}

data class GithubPullRequestPayload(
    val title: String,
    val body: String,
    val repositoryOwner: String,
    val riScId: String,
    val baseBranch: String,
) : GithubPostPayload {
    override fun toContentBody(): String =
        "{ \"title\":\"$title\", \"body\": \"$body\", \"head\": \"$repositoryOwner:$riScId\", \"base\": \"$baseBranch\" }"
}

class GithubClosePullRequestPayload : GithubPatchPayload {
    override fun toContentBody(): String =
        "{ \"title\":\"Closed\", \"body\": \"The PR was closed when risk scorecard was updated. " +
            "New approval from risk owner is required.\",  \"state\": \"closed\"}"
}

interface GithubPutPayload : GithubPayload

interface GithubPostPayload : GithubPayload

interface GithubPatchPayload : GithubPayload

interface GithubPayload {
    fun toContentBody(): String
}
