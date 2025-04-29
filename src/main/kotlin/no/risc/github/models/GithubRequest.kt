package no.risc.github.models

import kotlinx.serialization.Serializable
import no.risc.utils.KDateSerializer
import java.text.SimpleDateFormat
import java.util.Date

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

@Serializable
data class GithubCreateNewBranchPayload(
    val nameOfNewBranch: String,
    val shaOfLatestDefault: String,
) {
    fun toContentBody(): String = "{ \"ref\":\"$nameOfNewBranch\", \"sha\": \"$shaOfLatestDefault\" }"
}
