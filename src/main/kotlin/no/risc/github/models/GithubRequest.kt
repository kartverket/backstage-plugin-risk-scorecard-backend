package no.risc.github.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.risc.utils.KDateSerializer
import java.util.Date

/**
 * For use with GitHub's create or update file contents API endpoint.
 *
 * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#create-or-update-file-contents">
 *      Create or update file contents API documentation</a>
 */
@Serializable
data class GithubWriteToFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String,
    @SerialName("committer")
    val author: Author? = null,
)

/**
 * For use with GitHub's delete a file contents API endpoint.
 *
 * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#delete-a-file">Delete a file API
 *      documentation</a>
 */
@Serializable
data class GithubDeleteFilePayload(
    val message: String,
    val sha: String,
    val branch: String? = null,
    @SerialName("committer")
    val author: Author? = null,
)

/**
 * The author to create/update a file as.
 *
 * @see GithubWriteToFilePayload
 */
@Serializable
data class Author(
    val name: String?,
    val email: String?,
    @Serializable(KDateSerializer::class)
    val date: Date,
)

/**
 * For use with GitHub's create pull request API endpoint.
 *
 * @see <a href="https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#create-a-pull-request">Create a pull
 *      request API documentation</a>
 */
@Serializable
data class GithubCreateNewPullRequestPayload(
    val title: String,
    val body: String,
    val head: String,
    val base: String,
) {
    constructor(title: String, body: String, repositoryOwner: String, branch: String, baseBranch: String) : this(
        title = title,
        body = body,
        head = "$repositoryOwner:$branch",
        base = baseBranch,
    )
}

/**
 * For use with GitHub's create new branch API endpoint.
 *
 * @see <a href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#create-a-reference">Create a branch API
 *      documentation</a>
 */
@Serializable
data class GithubCreateNewBranchPayload(
    @SerialName("ref")
    val nameOfNewBranch: String,
    @SerialName("sha")
    val shaToBranchFrom: String,
)
