@file:OptIn(ExperimentalSerializationApi::class)

package no.risc.github

import kotlinx.serialization.ExperimentalSerializationApi
import no.risc.github.models.GithubCreateNewBranchPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class GithubHelper(
    @Value("\${filename.prefix}") private val filenamePrefix: String,
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
) {
    fun uriToFindRiScFiles(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath"

    fun repositoryContentsUri(
        owner: String,
        repository: String,
        path: String,
        branch: String? = null,
    ): String = branch?.let { "/$owner/$repository/contents/$path?ref=$branch" } ?: "/$owner/$repository/contents/$path"

    fun uriToFindRiSc(
        owner: String,
        repository: String,
        id: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$id.$filenamePostfix.yaml"

    fun uriToFindAllRiScBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"

    fun uriToGetRepositoryInfo(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository"

    fun uriToFindRiScOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

    /**
     * Constructs a URI to get the last commit on a provided branch.
     *
     * @see <a href="https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#get-a-commit">The get a
     *      commit API reference</a>
     */
    fun uriToGetLastCommitOnBranch(
        owner: String,
        repository: String,
        branchName: String,
    ): String = "/$owner/$repository/commits/heads/$branchName"

    fun uriToCreateNewBranch(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"

    fun uriToFetchAllPullRequests(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    fun uriToCreatePullRequest(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    fun uriToEditPullRequest(
        owner: String,
        repository: String,
        pullRequestNumber: Int,
    ): String = "/$owner/$repository/pulls/$pullRequestNumber"

    fun bodyToClosePullRequest(): String =
        "{ \"title\":\"Closed\", \"body\": \"The PR was closed when risk scorecard was updated. " +
            "New approval from risk owner is required.\",  \"state\": \"closed\"}"

    fun bodyToCreateNewBranchFromDefault(
        branchName: String,
        latestShaAtDefault: String,
    ): GithubCreateNewBranchPayload =
        GithubCreateNewBranchPayload(
            nameOfNewBranch = "refs/heads/$branchName",
            shaOfLatestDefault = latestShaAtDefault,
        )

    /**
     * Produces a URL to retrieve commits from the given repository (`owner/repository`). Commits are by default
     * retrieved for the default branch of the repository, unless a specific branch is given. Similarly, commits are
     * retrieved for the entire git history, unless a specific start date-time is given. If a RiSc ID is given, only
     * commits that change the content file for that RiSc are retrieved.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to retrieve commits from.
     * @param riScId The RiSc to retrieve commits for. Only applicable if given (`!= null`)
     * @param branch The branch to retrieve commits on. If not given (`!= null`), the default branch is used.
     * @param since The date-time to retrieve commits since.
     *
     * @see <a href="https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#list-commits">List commits
     *      API documentation</a>
     */
    fun uriToFetchCommits(
        owner: String,
        repository: String,
        riScId: String? = null,
        branch: String? = null,
        since: OffsetDateTime? = null,
    ): String =
        if (branch == null && riScId == null && since == null) {
            "/$owner/$repository/commits"
        } else {
            "/$owner/$repository/commits?${
                branch?.let { "sha=$branch&" } ?: ""
            }${
                since?.let { "since=$since&" } ?: ""
            }${
                riScId?.let { "path=$riScFolderPath/$riScId.$filenamePostfix.yaml" } ?: ""
            }"
        }
}
