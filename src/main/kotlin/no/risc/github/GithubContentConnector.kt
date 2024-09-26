package no.risc.github

import no.risc.github.models.Author
import no.risc.github.models.BranchInfo
import no.risc.github.models.CreateContentStatus
import no.risc.github.models.FileContentDTO
import no.risc.github.models.GithubBranchPayload
import no.risc.github.models.GithubClosePullRequestPayload
import no.risc.github.models.GithubPullRequestPayload
import no.risc.github.models.GithubUpdateFilePayload
import no.risc.github.models.PullRequestActionStatus
import no.risc.github.models.PullRequestStatus
import no.risc.github.models.ShaResponseDTO
import no.risc.github.models.UpdateContentStatus
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
import no.risc.risc.models.UserInfo
import no.risc.utils.encodeBase64
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GithubContentConnector(
    private val githubHelper: GithubHelper,
    private val githubWebClient: GithubWebClient,
) {
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
                githubWebClient
                    .post(
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
        contentAuthor: Author,
        accessToken: GithubAccessToken,
    ): CreateContentStatus {
        val result =
            try {
                githubWebClient
                    .put(
                        githubHelper.uriToPutRiScOnDraftBranch(owner, repository, riscId),
                        accessToken.value,
                        GithubUpdateFilePayload(
                            message = commitMessage,
                            content = fileContent.encodeBase64(),
                            sha = null,
                            branchName = riscId,
                            author = contentAuthor,
                        ),
                    ).fileContentResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not update RiSc with id: $riscId", e)

                null
            }

        return when (result.isNullOrBlank()) {
            true -> CreateContentStatus.Failure
            false -> CreateContentStatus.Success
        }
    }

    fun updateContent(
        owner: String,
        repository: String,
        sha: String,
        updatedFileContent: String,
        riscId: String,
        commitMessage: String = "Update RiSc with id: $riscId",
        contentAuthor: Author,
        accessToken: GithubAccessToken,
    ): UpdateContentStatus {
        val result =
            try {
                githubWebClient
                    .put(
                        githubHelper.uriToPutRiScOnDraftBranch(owner, repository, riscId),
                        accessToken.value,
                        GithubUpdateFilePayload(
                            message = commitMessage,
                            content = updatedFileContent.encodeBase64(),
                            sha = sha,
                            branchName = riscId,
                            author = contentAuthor,
                        ),
                    ).fileContentResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not update RiSc with id: $riscId", e)

                null
            }

        return when (result.isNullOrBlank()) {
            true -> UpdateContentStatus.Failure
            false -> UpdateContentStatus.Success
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
                githubWebClient
                    .get(
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

    fun getLatestShaOfBranch(
        owner: String,
        repository: String,
        branchName: String,
        accessToken: GithubAccessToken,
    ): BranchInfo {
        val result =
            try {
                githubWebClient
                    .get(
                        uri = githubHelper.uriToFindBranch(owner, repository, branchName),
                        accessToken = accessToken.value,
                    ).shaResponseDTO()
            } catch (e: Exception) {
                logger.error("Could not find main branch", e)

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
    ): PullRequestActionStatus {
        try {
            githubWebClient
                .post(
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

            return PullRequestActionStatus.Opened
        } catch (e: Exception) {
            logger.error("Could not create PR for RiSc with id: $riscId")
            return PullRequestActionStatus.Error
        }
    }

    fun closePR(
        owner: String,
        repository: String,
        accessToken: GithubAccessToken,
        riscId: String,
    ): PullRequestActionStatus {
        val pullRequestsForRiSc =
            try {
                githubWebClient
                    .get(
                        githubHelper.uriToFetchAllPullRequests(owner, repository),
                        accessToken = accessToken.value,
                    ).pullRequestResponseDTOs()
            } catch (e: Exception) {
                logger.error("Could not fetch PRs from repository: $repository", e)

                return PullRequestActionStatus.Error
            }

        when (pullRequestsForRiSc.isNotEmpty()) {
            true -> {
                val prForRiSc = pullRequestsForRiSc.find { it.head.ref == riscId }
                when (prForRiSc) {
                    null -> {}
                    else -> {
                        githubWebClient
                            .patch(
                                uri = githubHelper.uriToEditPullRequest(owner, repository, prForRiSc.number),
                                accessToken = accessToken.value,
                                githubPatchPayload = GithubClosePullRequestPayload(),
                            ).bodyToMono<String>()
                            .block()
                    }
                }
            }
            false -> {}
        }

        return PullRequestActionStatus.Closed
    }

    suspend fun doesPRExist(
        owner: String,
        repository: String,
        riscId: String,
        accessToken: GithubAccessToken,
    ): PullRequestStatus {
        val result =
            try {
                val githubResponse =
                    githubWebClient
                        .get(
                            uri = githubHelper.uriToFindAllRiScBranches(owner, repository),
                            accessToken.value,
                        ).awaitBody<List<GithubReferenceObjectDTO>>()
                        .map {
                            it.toInternal()
                        }

                githubResponse.riScIdentifiersDrafted()
            } catch (e: Exception) {
                logger.error("Could not fetch PRs", e)

                null
            }

        return when (result != null && result.any { it.id == riscId }) {
            true -> PullRequestStatus.PullRequestExist
            false -> PullRequestStatus.PullRequestDoesNotExist
        }
    }

    private fun List<GithubReferenceObject>.riScIdentifiersDrafted(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.ref.split("/").last(), RiScStatus.Draft) }

    private fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    private fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.shaResponseDTO(): String? = this.bodyToMono<ShaResponseDTO>().block()?.value

    private fun ResponseSpec.fileContentResponseDTO(): String? = this.bodyToMono<FileContentDTO>().block()?.value
}
