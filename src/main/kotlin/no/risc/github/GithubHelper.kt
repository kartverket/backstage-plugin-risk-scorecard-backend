@file:OptIn(ExperimentalSerializationApi::class)

package no.risc.github

import kotlinx.serialization.ExperimentalSerializationApi
import no.risc.config.InitRiScServiceConfig
import no.risc.github.models.GithubCreateNewBranchPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class GithubHelper(
    @Value("\${filename.prefix}") private val filenamePrefix: String,
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
    private val initRiScServiceConfig: InitRiScServiceConfig,
) {
    /**
     * Constructs the file path to the file for the given RiSc.
     *
     * @param riScId The ID of the RiSc.
     */
    internal fun riscPath(riScId: String): String = "$riScFolderPath/$riScId.$filenamePostfix.yaml"

    /**
     * Constructs a URI for performing file/directory operations (retrieval, update and deletion). If no branch is
     * provided, then the default branch is considered.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @param path The path of the file/directory to consider
     * @param branch The branch to perform the operations on. Only valid for retrieval.
     * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#get-repository-content">The
     *      get repository content API documentation</a>
     */
    fun repositoryContentsUri(
        owner: String,
        repository: String,
        path: String,
        branch: String? = null,
    ): String = "/$owner/$repository/contents/$path${branch?.let { "?ref=$branch" } ?: ""}"

    /**
     * Constructs a URI to get all RiSc files in the given repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#get-repository-content">The
     *      get repository content API documentation</a>
     */
    fun uriToFindRiScFiles(
        owner: String,
        repository: String,
    ): String = repositoryContentsUri(owner = owner, repository = repository, path = riScFolderPath)

    /**
     * Constructs a URI to get the contents of a specific RiSc.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @param id The id of the RiSc to get the contents for.
     * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#get-repository-content">The
     *      get repository content API documentation</a>
     */
    fun uriToFindRiSc(
        owner: String,
        repository: String,
        id: String,
    ): String =
        repositoryContentsUri(
            owner = owner,
            repository = repository,
            path = riscPath(id),
        )

    /**
     * Constructs a URI to get the contents of a specific RiSc on its draft branch.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @param id The id of the RiSc to get the contents for.
     * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#get-repository-content">The
     *      get repository content API documentation</a>
     */
    fun uriToFindRiScOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
    ): String =
        repositoryContentsUri(
            owner = owner,
            repository = repository,
            path = riscPath(riScId),
            branch = riScId,
        )

    /**
     * Constructs a URI to retrieve all draft branches for RiScs.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @see <a href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#list-matching-references">The list
     *      matching references API documentation</a>
     */
    fun uriToFindAllRiScBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"

    /**
     * Constructs a URI to retrieve information about the provided repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @see <a href="https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#get-a-repository">The get a
     *      repository API documentation</a>
     */
    fun uriToGetRepositoryInfo(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository"

    /**
     * Constructs a URI to get the last commit on a provided branch.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @param branchName The name of the branch.
     * @see <a href="https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#get-a-commit">The get a
     *      commit API documentation</a>
     */
    fun uriToGetLastCommitOnBranch(
        owner: String,
        repository: String,
        branchName: String,
    ): String = "/$owner/$repository/commits/heads/$branchName"

    /**
     * Constructs a URI to create new branches in the provided repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @see <a href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#create-a-reference">The create a
     *      reference API documentation</a>
     */
    fun uriToCreateNewBranch(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"

    /**
     * Constructs a URI to delete a branch in the provided repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @param branch The name of the branch to delete
     * @see <a href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#delete-a-reference">The delete a
     *      reference API documentation</a>
     */
    fun uriToDeleteBranch(
        owner: String,
        repository: String,
        branch: String,
    ): String = "/$owner/$repository/git/refs/heads/$branch"

    /**
     * Constructs a URI to fetch all pull requests in the repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @see <a href="https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#list-pull-requests">The list pull
     *      requests API documentation</a>
     */
    fun uriToFetchAllPullRequests(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    /**
     * Constructs a URI to create a pull request in the repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @see <a href="https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#create-a-pull-request">The create
     *      a pull request API documentation</a>
     */
    fun uriToCreatePullRequest(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    /**
     * Constructs a URI to create a pull request in the repository.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to consider.
     * @param pullRequestNumber The id of the pull request to edit
     * @see <a href="https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#update-a-pull-request">The update
     *      a pull request API documentation</a>
     */
    fun uriToEditPullRequest(
        owner: String,
        repository: String,
        pullRequestNumber: Int,
    ): String = "/$owner/$repository/pulls/$pullRequestNumber"

    /**
     * The default request body used for closing a pull request.
     *
     * @see uriToEditPullRequest
     */
    fun bodyToClosePullRequest(): String =
        "{ \"title\":\"Closed\", \"body\": \"The PR was closed when risk scorecard was updated. " +
            "New approval from risk owner is required.\",  \"state\": \"closed\"}"

    /**
     * Constructs a request body to create a new branch with.
     *
     * @param branchName The name of the new branch.
     * @param shaToBranchFrom The SHA of the commit to branch out from.
     * @see uriToCreateNewBranch
     */
    fun bodyToCreateNewBranch(
        branchName: String,
        shaToBranchFrom: String,
    ): GithubCreateNewBranchPayload =
        GithubCreateNewBranchPayload(
            nameOfNewBranch = "refs/heads/$branchName",
            shaToBranchFrom = shaToBranchFrom,
        )

    /**
     * Produces a URL to retrieve commits from the given repository (`owner/repository`). Commits are by default
     * retrieved for the default branch of the repository, unless a specific branch is given. Similarly, commits are
     * retrieved for the entire git history, unless a specific start date-time is given. If a RiSc ID is given, only
     * commits that change the content file for that RiSc are retrieved.
     *
     * Note: GitHub RESP API defaults to `per_page=30`if perPage is not provided. To retrieve more results you must
     * either pass `per_page`(e.g., 100) or iterate pages via `page`until fewer results are returned.
     *
     * @param owner The user/organization the repository belongs to.
     * @param repository The repository to retrieve commits from.
     * @param riScId The RiSc to retrieve commits for. Only applicable if given (`!= null`)
     * @param branch The branch to retrieve commits on. If not given (`!= null`), the default branch is used.
     * @param since The date-time to retrieve commits since.
     * @param perPage The number of commits to retrieve per page.
     * @param page The page number to retrieve.
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
        perPage: Int? = null,
        page: Int? = null,
    ): String {
        val base = "/$owner/$repository/commits"
        val queryParts = mutableListOf<String>()

        branch?.let { queryParts += ("sha=$it") }
        since?.let { queryParts += "since=$it" }
        riScId?.let { queryParts += "path=${riscPath(it)}" }
        perPage?.let { queryParts += "per_page=$it" }
        page?.let { queryParts += "page=$it" }

        return if (queryParts.isEmpty()) base else "$base?${queryParts.joinToString("&")}"
    }

    fun uriToInitRiscConfig(): String =
        repositoryContentsUri(
            initRiScServiceConfig.repoOwner,
            initRiScServiceConfig.repoName,
            "init-risc-def.json",
        )

    fun uriToInitRiSc(initRiScId: String) =
        repositoryContentsUri(
            initRiScServiceConfig.repoOwner,
            initRiScServiceConfig.repoName,
            "initial-riscs/$initRiScId.json",
        )
}
