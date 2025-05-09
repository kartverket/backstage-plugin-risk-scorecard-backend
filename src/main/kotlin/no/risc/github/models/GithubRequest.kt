package no.risc.github.models

import kotlinx.serialization.Serializable
import no.risc.utils.KDateSerializer
import java.text.SimpleDateFormat
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
    val branchName: String,
    val author: Author? = null,
) {
    fun toContentBody(): String =
        "{\"message\": \"$message\", \"content\": \"$content\", \"branch\": \"$branchName\"" +
            (author?.let { ", \"committer\": ${author.toJSONString()}" } ?: "") +
            (sha?.let { ", \"sha\": \"$sha\"" } ?: "") +
            "}"
}

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
) {
    private fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)

    fun toJSONString(): String = "{ \"name\":\"${name}\", \"email\":\"${email}\", \"date\":\"${formattedDate()}\" }"
}

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
    val repositoryOwner: String,
    val branch: String,
    val baseBranch: String,
) {
    fun toContentBody(): String =
        "{ \"title\":\"$title\", \"body\": \"$body\", \"head\": \"$repositoryOwner:$branch\", \"base\": \"$baseBranch\" }"
}

/**
 * For use with GitHub's create new branch API endpoint.
 *
 * @see <a href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#create-a-reference">Create a branch API
 *      documentation</a>
 */
@Serializable
data class GithubCreateNewBranchPayload(
    val nameOfNewBranch: String,
    val shaToBranchFrom: String,
) {
    fun toContentBody(): String = "{ \"ref\":\"$nameOfNewBranch\", \"sha\": \"$shaToBranchFrom\" }"
}
