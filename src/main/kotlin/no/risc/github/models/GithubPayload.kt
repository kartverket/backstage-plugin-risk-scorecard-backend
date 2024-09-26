package no.risc.github.models

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
